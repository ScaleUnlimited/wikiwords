package com.scaleunlimited.wikiwords.tools;

import info.bliki.wiki.dump.IArticleFilter;
import info.bliki.wiki.dump.Siteinfo;
import info.bliki.wiki.dump.WikiArticle;
import info.bliki.wiki.dump.WikiXMLParser;
import info.bliki.wiki.model.WikiModel;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.ccil.cowan.tagsoup.Parser;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.scaleunlimited.wikiwords.WikiTemplates;

public class MediaWikiTool {
    private static final Logger LOGGER = Logger.getLogger(MediaWikiTool.class);

    public MediaWikiTool() {
    }

    public static void main(String[] args) {
        try {
            MyArticleFilter filter = new MyArticleFilter();
            WikiXMLParser wxp = new WikiXMLParser(new File("src/test/resources/enwiki-snippet.xml"), filter);
            wxp.parse();
            
            // Run TagSoup and our handler on each HTML result
            Parser htmlParser = new Parser();
            MyHTMLHandler handler = new MyHTMLHandler();
            htmlParser.setContentHandler(handler);
            htmlParser.setErrorHandler(handler);
            
            File outputDir = new File("build/test/MediaWikiTool/snippets/");
            outputDir.mkdirs();
            
            Map<String, StringBuilder> pages = filter.getResult();
            for (String title : pages.keySet()) {
                InputSource is = new InputSource(new ByteArrayInputStream(pages.get(title).toString().getBytes(Charset.forName("UTF-8"))));
                is.setEncoding("UTF-8");
                htmlParser.parse(is);
                
                List<String> terms = handler.getTerms();
                for (ArticleLinkPosition articleLink : handler.getArticleLinks()) {
                    for (int i = Math.max(0, articleLink.getLinkPosition() - 100); i < Math.min(articleLink.getLinkPosition() + 100, terms.size()); i++) {
                        System.out.println(String.format("%s: %s %s %d", title, terms.get(i), articleLink.getArticle(), i - articleLink.getLinkPosition()));
                    }
                }
                
//                File outputFile = new File(outputDir, title.replaceAll(" ", "_") + ".html");
//                try (OutputStream os = new FileOutputStream(outputFile)) {
//                    IOUtils.write(pages.get(title), os);
//                }
            }
            
            System.out.println(filter.getTemplateCounts());
            
            System.out.println(filter.getCategories());
            
            System.out.println(filter.getRedirects());
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static class XMLPath {
        
        private List<String> _path;
        
        public XMLPath() {
            _path = new ArrayList<>();
        }

        public void pushNode(String qName) {
            _path.add(qName);
        }
        
        public void popNode(String qName) {
            // TODO verify that last item is == qName
            _path.remove(_path.size() - 1);
        }
        
        private boolean atNode(String nodePath) {
            String[] nodeNames = nodePath.split("/");
            boolean isAbsolutePath = nodePath.startsWith("/");

            if (nodeNames.length > _path.size()) {
                return false;
            } else if (isAbsolutePath && (nodeNames.length != _path.size())) {
                return false;
            }

            
            int targetPathIndex = nodeNames.length - 1;
            int curPathIndex = _path.size() - 1;
            for (; targetPathIndex >= 0; targetPathIndex--, curPathIndex--) {
                if (!nodeNames[targetPathIndex].equals(_path.get(curPathIndex))) {
                    return false;
                }
            }

            return true;
        }
        
        /**
         * Return true if our current path contains <nodePath>. This means we're at or
         * below (deeper) in the document hierarchy than whatever is in <nodePath>
         * 
         * @param nodePath
         * @return
         */
        private boolean inNode(String nodePath) {
            String[] nodeNames = nodePath.split("/");
            boolean isAbsolutePath = nodePath.startsWith("/");

            for (int startIndex = 0; startIndex <= _path.size() - nodeNames.length; startIndex++) {
                int targetPathIndex = 0;
                int curPathIndex = startIndex;

                for (; targetPathIndex < nodeNames.length; targetPathIndex++, curPathIndex++) {
                    if (!nodeNames[targetPathIndex].equals(_path.get(curPathIndex))) {
                        if (isAbsolutePath) {
                            return false;
                        } else {
                            break;
                        }
                    }
                }
                
                // If we matched the entire <nodePath> we're good.
                if (targetPathIndex == nodeNames.length){
                    return true;
                }
            }

            return false;
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
                    } catch (UnsupportedEncodingException e) {
                       throw new RuntimeException("Impossible encoding exception", e);
                    }
                    
                    // Screen out /File:xxx and /<lang>:xxx links
                    if (url.startsWith("/") && (url.indexOf(':') == -1)) {
                        int endIndex = url.indexOf('#') == -1 ? url.length() : url.indexOf('#');
                        _articleLinks.add(new ArticleLinkPosition(url.substring(1, endIndex), _terms.size()));
                    }
                }
                
            } else if (!INLINE_HTML_ELEMNTS.contains(qName)) {
                // depending on element, insert " " to break up text so we don't get words appended together
                _elementText.append(' ');
            }
            
        }
        
        private void addTermsFromElementText() {
            // TODO use real parser
            String[] terms = _elementText.toString().split("[ \t\r\n]");
            for (String term : terms) {
                term = term.trim();
                if (!term.isEmpty() && Character.isLetter(term.charAt(0))) {
                    _terms.add(term);
                }
            }
            
            _elementText.setLength(0);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (_path.atNode("head/title")) {
               _terms.add(_elementText.toString());
               _elementText.setLength(0);
            } else  if (_path.atNode("a")) {
                _terms.add(_anchorText.toString());
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
    
    private static class MyArticleFilter implements IArticleFilter {

        // Search for {{ or }}, across lines.
        private static Pattern TEMPLATE_PATTERN = Pattern.compile("(\\{\\{|\\}\\})", Pattern.DOTALL);
        
        private static Pattern CATEGORY_PATTERN = Pattern.compile("\\[\\[Category:(.+)\\]\\]");
        private static Pattern REDIRECT_PATTERN = Pattern.compile("#REDIRECT \\[\\[(.+?)\\]\\]");

        private Map<String, Integer> _templateCounts;
        private WikiTemplates _templates;
        private Map<String, Set<String>> _categories;
        private Map<String, String> _redirects;
        private StringBuilder _curPage;
        private Map<String, StringBuilder> _pages;
        
        public MyArticleFilter() throws IOException {
            _templateCounts = new HashMap<>();
            _categories = new HashMap<String, Set<String>>();
            _templates = new WikiTemplates();
            _redirects = new HashMap<>();
            _pages = new HashMap<>();
        }
        
        private void newPage(String article) {
            _curPage = new StringBuilder();
            _pages.put(article, _curPage);
        }
        
        private void endPage() {
            _curPage = null;
        }
        
        private void emit(String text) {
            _curPage.append(text);
        }
        
        @Override
        public void process(WikiArticle article, Siteinfo siteInfo) throws SAXException {
            if (article.isMain()) {
                String text = article.getText();
                
                if (article.isRedirect()) {
                    String redirectArticle = getRedirect(text);
                    if (redirectArticle != null) {
                        _redirects.put(article.getTitle().replaceAll(" ",  "_"), redirectArticle);
                    } else {
                        LOGGER.warn("Redirect article without #REDIRECT directive: " + article.getTitle());
                    }
                    
                    return;
                }
                
                String html = WikiModel.toHtml(text);
                String title = article.getTitle();
                
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
                        LOGGER.error("Invalid template nesting at offset " + m.start());
                        continue;
                    } else {
                        curDepth--;
                        
                        if (curDepth > 0) {
                            continue;
                        }
                    }
                    
                    // At this point we know we've found the end of the template.
                    // First output everything from curOffset to start of this chunk.
                    try {
                    emit(html.substring(curOffset, templateStart));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
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
                endPage();
            } else if (article.isCategory()) {
                String title = article.getTitle();
                if (!title.startsWith("Category:")) {
                    LOGGER.error("Invalid category page title: " + title);
                    return;
                }
                
                String categoryName = title.substring("Category:".length());
                String text = article.getText();
                Matcher m = CATEGORY_PATTERN.matcher(text);
                
                Set<String> parentCategories = new HashSet<>();
                while (m.find()) {
                    String parentCategory = m.group(1);
                    if (parentCategory.indexOf('|') != -1) {
                        parentCategory = parentCategory.substring(0, parentCategory.indexOf('|'));
                    }
                    parentCategories.add(parentCategory);
                }
                
                _categories.put(categoryName, parentCategories);
            }
        }
        
        private String getRedirect(String text) {
            Matcher m = REDIRECT_PATTERN.matcher(text);
            if (m.find()) {
                return m.group(1).replaceAll(" ", "_");
            } else {
                return null;
            }
        }

        public Map<String, Integer> getTemplateCounts() {
            return _templateCounts;
        }
        
        public Map<String, Set<String>> getCategories() {
            return _categories;
        }
        
        public Map<String, String> getRedirects() {
            return _redirects;
        }
        
        public Map<String, StringBuilder> getResult() {
            return _pages;
        }
    }
    
}
