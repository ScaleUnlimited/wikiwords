package com.scaleunlimited.wikiwords.flow;

import info.bliki.wiki.model.WikiModel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.ccil.cowan.tagsoup.Parser;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import cascading.flow.Flow;
import cascading.flow.FlowDef;
import cascading.flow.FlowProcess;
import cascading.operation.BaseOperation;
import cascading.operation.DebugLevel;
import cascading.operation.Function;
import cascading.operation.FunctionCall;
import cascading.operation.OperationCall;
import cascading.pipe.Each;
import cascading.pipe.Pipe;
import cascading.tap.SinkMode;
import cascading.tap.Tap;
import cascading.tuple.Fields;
import cascading.tuple.Tuple;
import cascading.tuple.TupleEntry;

import com.scaleunlimited.cascading.BasePath;
import com.scaleunlimited.cascading.BasePlatform;
import com.scaleunlimited.cascading.LoggingFlowProcess;
import com.scaleunlimited.wikiwords.WikiTemplates;
import com.scaleunlimited.wikiwords.WikiwordsCounters;
import com.scaleunlimited.wikiwords.WorkingConfig;
import com.scaleunlimited.wikiwords.XMLPath;
import com.scaleunlimited.wikiwords.datum.WikiTermDatum;
import com.scaleunlimited.wikiwords.tools.GenerateTermsTool.GenerateTermsOptions;

public class GenerateTermsFlow {
    private static final Logger LOGGER = Logger.getLogger(GenerateTermsFlow.class);

    private final static String ARTICLE_NAME_FN = "article_name";
    private final static String ARTICLE_TEXT_FN = "article_text";
    
    private final static Fields ARTICLE_FIELDS = new Fields(ARTICLE_NAME_FN, ARTICLE_TEXT_FN);

    public static Flow createFlow(GenerateTermsOptions options) throws Exception {
        
        // We're reading in text files, which we have to run through an HTML generator.
        BasePlatform platform = options.getPlatform(GenerateTermsFlow.class);
        BasePath inputPath = platform.makePath(options.getInputDirname());
        Tap sourceTap = platform.makeTap(platform.makeTextScheme(), inputPath, SinkMode.KEEP);
        Pipe p = new Pipe("text lines");
        p = new Each(p, new Fields("line"), new ExtractFields(), Fields.RESULTS);
        p = new Each(p, new ConvertToTerms(options.getMaxDistanceToLink()), Fields.RESULTS);
        
        BasePath outputPath = options.getWorkingSubdirPath(WorkingConfig.TERMS_SUBDIR_NAME);
        Tap sinkTap = platform.makeTap(platform.makeBinaryScheme(WikiTermDatum.FIELDS), outputPath, SinkMode.REPLACE);
        
        FlowDef flowDef = new FlowDef()
            .setName("Generate terms")
            .setDebugLevel(options.isDebug() ? DebugLevel.VERBOSE : DebugLevel.NONE)
            .addSource(p, sourceTap)
            .addTailSink(p, sinkTap);
        
        return platform.makeFlowConnector().connect(flowDef);
    }

    /**
     * Take a line of text generated by the WikiDumpTool, and decode the MediaWiki markup, and
     * put the result into another tuple (along with the article name).
     *
     */
    @SuppressWarnings("serial")
    private static class ExtractFields extends BaseOperation<Void> implements Function<Void> {
        
        private transient TupleEntry _result;
        private transient LoggingFlowProcess _flowProcess;
        
        public ExtractFields() {
            super(1, ARTICLE_FIELDS);
        }

        @Override
        public void prepare(FlowProcess flowProcess, OperationCall<Void> operationCall) {
            super.prepare(flowProcess, operationCall);
            
            _result = new TupleEntry(ARTICLE_FIELDS, Tuple.size(ARTICLE_FIELDS.size()));
            _flowProcess = new LoggingFlowProcess(flowProcess);
        }
        
        @Override
        public void operate(FlowProcess flowProcess, FunctionCall<Void> functionCall) {
            String line = functionCall.getArguments().getString("line");
            String[] pieces = line.split("\t");
            if (pieces.length != 2) {
                throw new IllegalArgumentException("Got invalid line: " + line);
            }
            
            
            _result.setString(ARTICLE_NAME_FN, pieces[0]);
            try {
                _result.setString(ARTICLE_TEXT_FN, new String(Base64.decodeBase64(pieces[1].getBytes("us-ascii")), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("Impossible encoding exception", e);
            }
            
            functionCall.getOutputCollector().add(_result);
            _flowProcess.increment(WikiwordsCounters.ARTICLES, 1);
        }
        
    }
    
    /**
     * Convert the MediaWiki markup to HTML, then parse that and extract the terms.
     *
     */
    @SuppressWarnings("serial")
    private static class ConvertToTerms extends BaseOperation<Void> implements Function<Void> {

        // Search for {{ or }}, across lines.
        private static Pattern TEMPLATE_PATTERN = Pattern.compile("(\\{\\{|\\}\\})", Pattern.DOTALL);

        private int _maxDistanceToLink;
        
        private transient WikiTermDatum _result;
        private transient WikiTemplates _templates;
        private transient StringBuilder _curPage;
        private transient Parser _htmlParser;
        private transient MyHTMLHandler _handler;
        private transient Map<String, Integer> _templateCounts;
        private transient LoggingFlowProcess _flowProcess;

        public ConvertToTerms(int maxDistanceToLink) {
            super(ARTICLE_FIELDS.size(), WikiTermDatum.FIELDS);
            
            _maxDistanceToLink = maxDistanceToLink;
        }

        @Override
        public void prepare(FlowProcess flowProcess, OperationCall<Void> operationCall) {
            super.prepare(flowProcess, operationCall);

            _result = new WikiTermDatum();
            _htmlParser = new Parser();

            _handler = new MyHTMLHandler();
            _htmlParser.setContentHandler(_handler);
            _htmlParser.setErrorHandler(_handler);

            try {
                _templates = new WikiTemplates();
            } catch (IOException e) {
                throw new RuntimeException("Error instantiating WikiTemplates", e);
            }

            _templateCounts = new HashMap<>();
            
            _flowProcess = new LoggingFlowProcess<>(flowProcess);
        }

        @Override
        public void operate(FlowProcess flowProcess, FunctionCall<Void> functionCall) {
            TupleEntry te = functionCall.getArguments();

            String title = te.getString(ARTICLE_NAME_FN);
            String html = WikiModel.toHtml(te.getString(ARTICLE_TEXT_FN));

            newPage(title);
            emit("<html lang=\"en\">");
            emit("<head>");
            emit("<meta http-equiv=\"content-type\" content=\"text/html; charset=utf-8\">");
            emit("<title>");
            emit(title);
            emit("</title>");
            emit("</head>");
            emit("<body>");

            Matcher m = TEMPLATE_PATTERN.matcher(html);

            int curOffset = 0;
            int curDepth = 0;
            int templateStart = 0;

            while (m.find()) {
                if (m.group(1).equals("{{")) {
                    curDepth++;
                    if (curDepth == 1) {
                        templateStart = m.start();
                    }

                    continue;
                } else if (curDepth == 0) {
                    // We got an ending }} but we're not nested - ignore it.
                    LOGGER.warn("Invalid template nesting at offset " + m.start());
                    continue;
                } else {
                    curDepth--;

                    if (curDepth > 0) {
                        continue;
                    }
                }

                // At this point we know we've found the end of the template.
                // First output everything from curOffset to start of this chunk.
                emit(html.substring(curOffset, templateStart));

                int templateEnd = m.start() + 2;
                String templatePlusArgs = html.substring(templateStart + 2, templateEnd - 2);

                // Now run the template through our system
                String htmlSnippet = _templates.convertToHTML(templatePlusArgs);

                // Keep track of unknown templates
                if (htmlSnippet == null) {
                    String template = _templates.getTemplateName(templatePlusArgs);
                    Integer curCount = _templateCounts.get(template);
                    if (curCount == null) {
                        _templateCounts.put(template, 1);
                    } else {
                        _templateCounts.put(template, curCount + 1);
                    }
                } else {
                    emit(htmlSnippet);
                }

                // Advance to the next position.
                curOffset = templateEnd;
            }

            // Output the last bit
            emit(html.substring(curOffset));
            emit("</body>");
            emit("</html>");

            // Run TagSoup and our handler on each HTML result
            InputSource is = new InputSource(new ByteArrayInputStream(_curPage.toString().getBytes(Charset.forName("UTF-8"))));
            is.setEncoding("UTF-8");

            try {
                _htmlParser.parse(is);
                _flowProcess.increment(WikiwordsCounters.HTML_PARSE, 1);
                
                List<String> terms = _handler.getTerms();
                _result.setArticle(title);
                for (ArticleLinkPosition articleLink : _handler.getArticleLinks()) {
                    _result.setArticleRef(articleLink.getArticle());
                    for (int i = Math.max(0, articleLink.getLinkPosition() - _maxDistanceToLink); i < Math.min(articleLink.getLinkPosition() + _maxDistanceToLink, terms.size()); i++) {
                        _result.setTerm(terms.get(i));
                        _result.setDistance(i - articleLink.getLinkPosition());
                        functionCall.getOutputCollector().add(_result.getTupleEntry());
                        _flowProcess.increment(WikiwordsCounters.WIKITERM, 1);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Exception parsing HTML for " + title, e);
                _flowProcess.increment(WikiwordsCounters.HTML_PARSE_ERROR, 1);
            }
        }


        private void newPage(String article) {
            _curPage = new StringBuilder();
        }

        private void emit(String text) {
            _curPage.append(text);
        }
    }
    
    private static class MyHTMLHandler extends DefaultHandler {
        
        @SuppressWarnings("serial")
        private static Set<String> INLINE_HTML_ELEMNTS = new HashSet<String>() {{
            add("b");
            add("big");
            add("i");
            add("small");
            add("tt");

            add("abbr");
            add("acronym");
            add("cite");
            add("code");
            add("dfn");
            add("em");
            add("kbd");
            add("strong");
            add("samp");
            add("time");
            add("var");

            add("a");
            add("bdo");
            add("img");
            add("map");
            add("object");
            add("q");
            add("script");
            add("span");
            add("sub");
            add("sup");

            add("button");
            add("input");
            add("label");
            add("select");
            add("textarea");
        }};
        
        private XMLPath _path;
        private StringBuffer _elementText;
        private StringBuffer _anchorText;
        private List<String> _terms;
        private List<ArticleLinkPosition> _articleLinks;
        
        public MyHTMLHandler() {
        }
        
        @Override
        public void startDocument() throws SAXException {
            super.startDocument();
            
            _path = new XMLPath();
            _terms = new ArrayList<>();
            _elementText = new StringBuffer();
            _anchorText = new StringBuffer();
            _articleLinks = new ArrayList<>();
        }
        
        @Override
        public void endDocument() throws SAXException {
            // Flush out terms from preceeding text.
            addTermsFromElementText();

            super.endDocument();
        }
        
        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            super.startElement(uri, localName, qName, attributes);
            _path.pushNode(qName);
            
            if (_path.atNode("head/title")) {
                _elementText.setLength(0);
            } else if (_path.atNode("a")) {
                _anchorText.setLength(0);
                
                // Flush out terms from preceeding text.
                addTermsFromElementText();
                
                // Now if this is a Wikipedia link, we want to save the index of the title.
                String url = attributes.getValue("href");
                if (url != null) {
                    try {
                        url = URLDecoder.decode(url, "UTF-8");
                        
                        // Screen out /File:xxx and /<lang>:xxx links
                        if (url.startsWith("/") && (url.indexOf(':') == -1)) {
                            int endIndex = url.indexOf('#') == -1 ? url.length() : url.indexOf('#');
                            _articleLinks.add(new ArticleLinkPosition(url.substring(1, endIndex), _terms.size()));
                        }
                   } catch (UnsupportedEncodingException e) {
                       throw new RuntimeException("Impossible encoding exception", e);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Invalid URL hex sequence in " + url, e);
                    }
                }
                
            } else if (!INLINE_HTML_ELEMNTS.contains(qName)) {
                // depending on element, insert " " to break up text so we don't get words appended together
                _elementText.append(' ');
            }
            
        }
        
        private void addTermsFromElementText() {
            // TODO use real parser. Make it something we pass in.
            BreakIterator iter = BreakIterator.getWordInstance();
            String text = _elementText.toString();
            iter.setText(text);
            
            int start = iter.first();
            for (int end = iter.next(); end != BreakIterator.DONE; start = end, end = iter.next()) {
                 addTerm(text.substring(start, end));
            }
            
            _elementText.setLength(0);
        }

        private boolean addTerm(String term) {
            term = term.toLowerCase();
            term = StringUtils.strip(term);
            term = StringUtils.strip(term, "[](),?!;:.'\"");
            if (!term.isEmpty() && Character.isLetter(term.charAt(0))) {
                _terms.add(term);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (_path.atNode("head/title")) {
               addTerm(_elementText.toString());
               _elementText.setLength(0);
            } else  if (_path.atNode("a")) {
                addTerm(_anchorText.toString());
            }
            
            _path.popNode(qName);
            super.endElement(uri, localName, qName);
        }
        
        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            super.characters(ch, start, length);
            
            if (_path.inNode("a")) {
                _anchorText.append(ch, start, length);
            } else {
                // TODO do we need to worry about text in a <script> tag?
                _elementText.append(ch, start, length);
            }
        }
        
        public List<ArticleLinkPosition> getArticleLinks() {
            return _articleLinks;
        }
        
        public List<String> getTerms() {
            return _terms;
        }
    }
    
    private static class ArticleLinkPosition {
        private String _article;
        private int _linkPosition;
        
        public ArticleLinkPosition(String article, int linkPosition) {
            _article = article;
            _linkPosition = linkPosition;
        }

        public String getArticle() {
            return _article;
        }

        public int getLinkPosition() {
            return _linkPosition;
        }
    }

}
