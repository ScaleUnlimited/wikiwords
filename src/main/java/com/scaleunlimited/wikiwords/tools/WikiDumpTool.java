package com.scaleunlimited.wikiwords.tools;

import info.bliki.wiki.dump.IArticleFilter;
import info.bliki.wiki.dump.Siteinfo;
import info.bliki.wiki.dump.WikiArticle;
import info.bliki.wiki.dump.WikiXMLParser;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.FileWriterWithEncoding;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.xml.sax.SAXException;

public class WikiDumpTool {
    private static final Logger LOGGER = Logger.getLogger(WikiDumpTool.class);

    public static final String MAIN_PAGE_COUNTER = "main-page";
    public static final String REDIRECT_PAGE_COUNTER = "redirect-valid-page";
    public static final String REDIRECT_INVALID_PAGE_COUNTER = "redirect-invalid-page";
    public static final String PROJECT_PAGE_COUNTER = "project-page";
    public static final String FILE_PAGE_COUNTER = "file-page";
    public static final String TEMPLATE_PAGE_COUNTER = "template-page";
    public static final String CATEGORY_PAGE_COUNTER = "category-valid-page";
    public static final String CATEGORY_INVALID_PAGE_COUNTER = "category-invalid-page";
    public static final String MODULE_PAGE_COUNTER = "module-page";
    public static final String DISAMBIGUATION_PAGE_COUNTER = "disambiguation-page";
    public static final String UNKNOWN_PAGE_COUNTER = "unknown-page";
    
    public static final String EXCEPTION_COUNTER = "exception";
    
    private List<Exception> _exceptions;
    
    public WikiDumpTool() {
        // TODO Auto-generated constructor stub
    }
    
    public Map<String, Integer> run(String inputFilename, String outputDirname, String metadataDirname, int pagesPerFile, int numPages) throws IOException, SAXException {
        return run(inputFilename, outputDirname, metadataDirname, pagesPerFile, numPages, false, 1.0f);
    }
    
    public Map<String, Integer> run(String inputFilename, String outputDirname, String metadataDirname, int pagesPerFile, int numPages, boolean compressPartFiles, float samplePercent) throws IOException, SAXException {
        File outputDir = new File(outputDirname);
        if (!outputDir.exists()) {
            throw new InvalidParameterException("Output directory must exist: " + outputDir);
        } else if (!outputDir.isDirectory()) {
            throw new InvalidParameterException("Output directory can't be a file: " + outputDir);
        }
        
        FileUtils.cleanDirectory(outputDir);
        
        File metadataDir = new File(metadataDirname);
        if (!metadataDir.exists()) {
            throw new InvalidParameterException("Metadata directory must exist: " + metadataDir);
        } else if (!metadataDir.isDirectory()) {
            throw new InvalidParameterException("Metadata directory can't be a file: " + metadataDir);
        }

        FileUtils.cleanDirectory(metadataDir);

        File inputFile = new File(inputFilename);
        if (!inputFile.exists()) {
            throw new InvalidParameterException("Input file must exist: " + inputFile);
        } else if (inputFile.isDirectory()) {
            throw new InvalidParameterException("Input file can't be a directory: " + inputFile);
        }

        WikiDumpFilter filter = new WikiDumpFilter(outputDir, pagesPerFile, numPages, compressPartFiles, samplePercent);
        WikiXMLParser wxp = new WikiXMLParser(inputFile, filter);
        wxp.parse();

        filter.close();
        
        // Save off category hierarchy
        Map<String, Set<String>> categories = filter.getCategories();
        File categoryFile = new File(metadataDir, "categories.txt");
        categoryFile.delete();
        
        // Note that we write out all entries, even if they don't have any parent category
        try (BufferedWriter bw = new BufferedWriter(new FileWriterWithEncoding(categoryFile, "UTF-8"))) {
            for (String childCategory: categories.keySet()) {
                bw.write(childCategory);
                bw.write('\t');
                boolean firstParent = true;
                for (String parentCategory : categories.get(childCategory)) {
                    if (!firstParent) {
                        bw.write('|');
                    }
                    
                    firstParent = false;
                    bw.write(parentCategory);
                }
                
                bw.write('\n');
            }
            
            bw.flush();
        } catch (Exception e) {
            LOGGER.error("Exception saving category info", e);
        }
        
        // Save off redirect info
        Map<String, String> redirects = filter.getRedirects();
        File redirectFile = new File(metadataDir, "redirects.txt");
        redirectFile.delete();
        
        try (BufferedWriter bw = new BufferedWriter(new FileWriterWithEncoding(redirectFile, "UTF-8"))) {
            for (String redirectFrom: redirects.keySet()) {
                bw.write(redirectFrom);
                bw.write('\t');
                bw.write(redirects.get(redirectFrom));
                bw.write('\n');
            }
            
            bw.flush();
        } catch (Exception e) {
            LOGGER.error("Exception saving redirect info", e);
        }

        _exceptions = filter.getExceptions();
        
        return filter.getCounters();
    }

    public boolean hasExceptions() {
        return !_exceptions.isEmpty();
    }
    
    public List<Exception> getExceptions() {
        return _exceptions;
    }

    public static void main(String[] args) {
        WikiDumpOptions options = new WikiDumpOptions();
        CmdLineParser parser = new CmdLineParser(options);
        
        try {
            parser.parseArgument(args);
            
            WikiDumpTool tool = new WikiDumpTool();
            Map<String, Integer> counters = tool.run(options.getInputFile(), options.getOutputDir(), options.getMetadataDirname(), options.getPagesPerFile(), options.getNumPages(), options.isCompressed(), options.getSamplePercent());
            for (String counter : counters.keySet()) {
                LOGGER.info(String.format("%s: %d", counter, counters.get(counter)));
            }
            
            if (tool.hasExceptions()) {
                throw new RuntimeException("Exceptions while processing file, including: " + tool.getExceptions().get(0));
            }
        } catch(CmdLineException e) {
            System.err.println(e.getMessage());
            printUsageAndExit(parser);
        } catch (Throwable t) {
            System.err.println(t.getMessage());
            System.exit(-1);
        }
    }

    private static void printUsageAndExit(CmdLineParser parser) {
        parser.printUsage(System.err);
        System.exit(-1);
    }

    protected static class WikiDumpFilter implements IArticleFilter, Closeable {

        private static Pattern CATEGORY_PATTERN = Pattern.compile("\\[\\[Category:(.+)\\]\\]", Pattern.CASE_INSENSITIVE);
        private static Pattern REDIRECT_PATTERN = Pattern.compile("#REDIRECT[ \t]*:*[ \n]*\\[\\[(.+?)(#.+?|)\\]\\]", Pattern.CASE_INSENSITIVE);

        private static Pattern makeTemplatePattern(String templateName) {
            return Pattern.compile("\\{\\{[ \t]*" + templateName + "[ \t]*(\\|.*|)\\}\\}", Pattern.CASE_INSENSITIVE);
        }
        
        // List of templates found on https://en.wikipedia.org/wiki/Category:Disambiguation_message_boxes
        // and described on https://en.wikipedia.org/wiki/Template:Disambiguation. This is a dynamic set,
        // so unfortunately it can quickly become out of date.
        
        // FUTURE - process templates to find those triggering disambiguation (via usage of Dmbox template)
        private static Pattern[] DISAMBIGUATION_PATTERNS = new Pattern[] {
            // {{disambiguation|geo|ship}}
            makeTemplatePattern("disambiguation"),
            // {{disambig}}
            makeTemplatePattern("disambig"),
            // {{Disambig-Plants}}
            makeTemplatePattern("disambig\\-[^ \t]+"),
            // {{Disamb}}
            makeTemplatePattern("disamb"),
            // {{DAB}}
            makeTemplatePattern("dab"),
            // {{Disambiguation cleanup}}
            makeTemplatePattern("disambiguation[ \t]+cleanup"),
            // {{Airport disambiguation}}
            makeTemplatePattern("[^ \t]+[ \t]+disambiguation"),
            // {{Numberdis}}
            makeTemplatePattern("numberdis"),
            // {{Letter-NumberCombDisambig}}
            makeTemplatePattern("Letter\\-NumberCombDisambig"),
            // {{Hndis}}
            makeTemplatePattern("hndis"),
            // {{Hndis-cleanup}}
            makeTemplatePattern("hndis\\-cleanup"),
            // {{Geodis}}
            makeTemplatePattern("geodis"),
            // {{Mil-unit-dis}}
            makeTemplatePattern("Mil\\-unit\\-dis"),
        };
        
        private boolean _compressPartFiles;
        private float _samplePercent;
        private File _outputDir;
        private int _pagesPerFile;
        private int _numPages;
        private int _curPage;
        private int _curPart;
        
        private BufferedWriter _writer;
        private List<Exception> _exceptions;
        private Map<String, Integer> _counters;
        private Map<String, Set<String>> _categories;
        private Map<String, String> _redirects;
        
        private Random _rand;
        
        public WikiDumpFilter(File outputDir, int pagesPerFile, int numPages, boolean compressPartFiles, float samplePercent) throws IOException {
            _outputDir = outputDir;
            _compressPartFiles = compressPartFiles;
            _samplePercent = samplePercent;
            _pagesPerFile = pagesPerFile;
            _numPages = numPages;
            _curPage = 0;
            _curPart = 0;
            
            _exceptions = new ArrayList<>();
            _counters = new HashMap<>();
            _categories = new HashMap<String, Set<String>>();
            _redirects = new HashMap<>();
            
            _writer = makePartFileWriter();
            
            _rand = new Random(0L);
        }

        @Override
        public void process(WikiArticle article, Siteinfo siteInfo) throws SAXException {
            if (_curPage >= _numPages) {
                return;
            }
            
            String title = article.getTitle();
            String text = article.getText();

            // See if we want to save the article.
            if (article.isCategory()) {
                if (!title.startsWith("Category:")) {
                    LOGGER.error("Invalid category page title: " + title);
                    incrementCounter(CATEGORY_INVALID_PAGE_COUNTER);
                } else {
                    String categoryName = title.substring("Category:".length());
                    Set<String> parentCategories = getParentCategories(text);
                    _categories.put(categoryName, parentCategories);
                    incrementCounter(CATEGORY_PAGE_COUNTER);
                }
            } else if (article.isFile()) {
                incrementCounter(FILE_PAGE_COUNTER);
            } else if (article.isTemplate()) {
                incrementCounter(TEMPLATE_PAGE_COUNTER);
            } else if (article.isProject()) {
                incrementCounter(PROJECT_PAGE_COUNTER);
            } else if (article.isModule()) {
                incrementCounter(MODULE_PAGE_COUNTER);
            } else if (!article.isMain()) {
                incrementCounter(UNKNOWN_PAGE_COUNTER);
            } else if (article.isRedirect()) {
                // Redirect is a main page with an extra flag.
                String redirectArticle = getRedirect(text);
                if (redirectArticle != null) {
                    _redirects.put(title.replaceAll(" ",  "_"), redirectArticle);
                    incrementCounter(REDIRECT_PAGE_COUNTER);
                } else {
                    LOGGER.warn(String.format("Redirect article without #REDIRECT directive on page %s: %s", title, text));
                    incrementCounter(REDIRECT_INVALID_PAGE_COUNTER);
                }
            } else if (title.contains("(disambiguation)")) {
                if (!isDisambiguation(text)) {
                    LOGGER.warn(String.format("Disambiguation article without disambiguation template on page %s: %s", title, text));
                }
                
                incrementCounter(DISAMBIGUATION_PAGE_COUNTER);
            } else if (isDisambiguation(text)) {
                incrementCounter(DISAMBIGUATION_PAGE_COUNTER);
            } else {
                if (_samplePercent != 1.0f) {
                    // If the percent was 1.0, we should always process it.
                    if (_rand.nextFloat() > _samplePercent) {
                        return;
                    }
                }
                
                try {
                    int partNumber = _curPage / _pagesPerFile;
                    if (partNumber > _curPart) {
                        _writer.close();

                        _curPart = partNumber;
                        _writer = makePartFileWriter();
                    }

                    // Write out this page, and increment counts.
                    _writer.append(cleanText(title));
                    _writer.append('\t');
                    _writer.append(encodeText(text));
                    _writer.append('\n');

                    _curPage += 1;
                    incrementCounter(MAIN_PAGE_COUNTER);
                } catch (IOException e) {
                    LOGGER.error("Exception saving main page", e);
                    incrementCounter(EXCEPTION_COUNTER);
                    addException(e);
                }
            }
        }

        protected String getRedirect(String text) {
            Matcher m = REDIRECT_PATTERN.matcher(text);
            if (m.find()) {
                return m.group(1).replaceAll(" ", "_");
            } else {
                return null;
            }
        }

        protected boolean isDisambiguation(String text) {
            for (Pattern p : DISAMBIGUATION_PATTERNS) {
                Matcher m = p.matcher(text);
                if (m.find()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Extract all of the category refs from this page's <text>
         * We assume <text> has not yet been converted to HTML, so it
         * consists of MediaWiki markup.
         * 
         * @param text MediaWiki markup text from a page.
         * @return Set of parent category names.
         */
        private Set<String> getParentCategories(String text) {
            Matcher m = CATEGORY_PATTERN.matcher(text);

            Set<String> parentCategories = new HashSet<>();
            while (m.find()) {
                String parentCategory = m.group(1);
                if (parentCategory.indexOf('|') != -1) {
                    parentCategory = parentCategory.substring(0, parentCategory.indexOf('|'));
                }
                parentCategories.add(parentCategory);
            }
            
            return parentCategories;
        }
        
        private int incrementCounter(String counter) {
            Integer curCount = _counters.get(counter);
            int newCount;
            if (curCount == null) {
                newCount = 1;
            } else {
                newCount = 1 + curCount;
            }
            
            _counters.put(counter, newCount);
            return newCount;
        }
        
        private String cleanText(String title) {
            return title.replaceAll("[\t\n\r]", " ");
        }
        
        private String encodeText(String text) {
            try {
                return new String(Base64.encodeBase64(text.getBytes("UTF-8")), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("Impossible encoding exception", e);
            }
        }

        @Override
        public void close() throws IOException {
            _writer.close();
            _writer = null;
        }
        
        public boolean hasExceptions() {
            return !_exceptions.isEmpty();
        }
        
        public List<Exception> getExceptions() {
            return _exceptions;
        }
        
        public Map<String, Integer> getCounters() {
            return _counters;
        }
        
        public Map<String, Set<String>> getCategories() {
            return _categories;
        }
        
        public Map<String, String> getRedirects() {
            return _redirects;
        }
        
        private void addException(IOException e) {
            if (_exceptions.size() < 100) {
                _exceptions.add(e);
            }
        }

        private BufferedWriter makePartFileWriter() throws IOException {
            File f = new File(_outputDir, String.format("part-%03d.%s", _curPart, _compressPartFiles ? "gz" : "txt"));
            LOGGER.info("Writing to new part file: " + f);
            OutputStream os = new FileOutputStream(f);
            
            if (_compressPartFiles) {
                os = new GZIPOutputStream(os);
            }
            
            return new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
        }
    }
    
    private static class WikiDumpOptions {
        private boolean _debug = false;
        private boolean _compress = false;
        
        private String _inputFile;
        private String _outputDir;
        private String _metadataDirname;
        private int _pagesPerFile = 100000;
        private int _numPages = Integer.MAX_VALUE;
        private float _samplePercent = 1.0f;
        
        @Option(name = "-samplepercent", usage = "sample percentage (e.g. 10.0 for 10%)", required = false)
        public void setSamplePercent(float samplePercent) {
            _samplePercent = samplePercent / 100.0f;
        }

        public float getSamplePercent() {
            return _samplePercent;
        }

        @Option(name = "-debug", usage = "debug logging", required = false)
        public void setDebugLogging(boolean debug) {
            _debug = debug;
        }

        public boolean isDebugLogging() {
            return _debug;
        }

        @Option(name = "-compress", usage = "compress part files", required = false)
        public void setCompress(boolean compress) {
            _compress = compress;
        }

        public boolean isCompressed() {
            return _compress;
        }

        @Option(name = "-numpages", usage = "number of pages to output", required = false)
        public void setNumPages(int numPages) {
            _numPages = numPages;
        }

       public int getNumPages() {
            return _numPages;
        }

        @Option(name = "-pagesperfile", usage = "number of pages per output file", required = false)
        public void setPagesPerFile(int pagesPerFile) {
            _pagesPerFile = pagesPerFile;
        }

        public int getPagesPerFile() {
            return _pagesPerFile;
        }

        @Option(name = "-inputfile", usage = "path to Wikipedia dump file", required = true)
        public void setInputFile(String inputFile) {
            _inputFile = inputFile;
        }

        public String getInputFile() {
            return _inputFile;
        }

        @Option(name = "-outputdir", usage = "path to directory for part-xxx results", required = true)
        public void setOutputDir(String outputDir) {
            _outputDir = outputDir;
        }

        public String getOutputDir() {
            return _outputDir;
        }

        @Option(name = "-metadatadir", usage = "path to directory for metadata results", required = true)
        public void setMetadataDirname(String metadataDirname) {
            _metadataDirname = metadataDirname;
        }

        public String getMetadataDirname() {
            return _metadataDirname;
        }

        @Override
        public String toString() {
            return ReflectionToStringBuilder.toString(this, ToStringStyle.MULTI_LINE_STYLE);
        }

    }
}
