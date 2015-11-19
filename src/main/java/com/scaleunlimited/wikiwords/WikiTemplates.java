package com.scaleunlimited.wikiwords;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

public class WikiTemplates {
    private static final Logger LOGGER = Logger.getLogger(WikiTemplates.class);

    private List<Pattern> _patterns;
    private List<String> _actions;
    
    public WikiTemplates() throws IOException {
        _patterns = new ArrayList<>();
        _actions = new ArrayList<>();
        
        List<String> lines = IOUtils.readLines(WikiTemplates.class.getResourceAsStream("/wiki-templates.txt"));
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            String[] pieces = line.split(" => ");
            if (pieces.length != 2) {
                throw new IllegalArgumentException("Invalid line in template patterns: " + line);
            }
            
            Pattern p = Pattern.compile(pieces[0].trim(), Pattern.DOTALL);
            String action = pieces[1];
            if (action.indexOf('#') != -1) {
                action = action.substring(0, action.indexOf('#'));
            }
            
            action = action.trim();
            _patterns.add(p);
            _actions.add(action);
        }
    }
    
    public String getTemplateName(String templatePlusArgs) {
        String[] pieces = templatePlusArgs.split("\\|");
        if (pieces.length == 0) {
            // Can happen if we get passed a string containing only '|' chars.
            return "";
        }
        
        String template = pieces[0];
        int namespaceIndex = template.indexOf(':');
        if (namespaceIndex != -1) {
            template = template.substring(0, namespaceIndex);
        }
        
        return template.trim().toLowerCase();
    }

    public String convertToHTML(String templatePlusArgs) {
        String template = getTemplateName(templatePlusArgs);
        String action = getAction(template);
        
        // Handle aliases by looping.
        while (true) {
            if (action == null) {
                return null;
            } else if (action.equals("ignore") || action.equals("unknown")) {
                return "";
            } else if (action.startsWith("alias:")) {
                action = getAction(action.substring("alias:".length()));
            } else if (action.startsWith("constant:")) {
                return action.substring("constant:".length());
            } else if (action.equals("text")) {
                StringBuilder result = new StringBuilder();
                for (String param : getAnonymousParameters(templatePlusArgs)) {
                    result.append(' ');
                    result.append(convertMarkupToHTML(param));
                }
                
                result.append(", ");
                return result.toString();
            } else if (action.equals("articles")) {
                StringBuilder result = new StringBuilder();
                for (String param : getAnonymousParameters(templatePlusArgs)) {
                    result.append(String.format(" <a href=\"http://en.wikipedia.org/wiki/%s\">%s</a> ", param.replaceAll(" ", "_"), param));
                }
                
                return result.toString();
            } else {
                LOGGER.warn(String.format("Template '%s' has unknown action '%s'", template, action));
                return "";
            }
        }
    }
    
    public String convertToText(String templatePlusArgs) {
        String template = getTemplateName(templatePlusArgs);

        String action = getAction(template);
        
        // Handle aliases by looping.
        while (true) {
            if (action == null) {
                return null;
            } else if (action.equals("ignore") || action.equals("unknown")) {
                return "";
            } else if (action.startsWith("alias:")) {
                action = getAction(action.substring("alias:".length()));
            } else if (action.startsWith("constant:")) {
                return action.substring("constant:".length());
            } else if (action.equals("text")) {
                StringBuilder result = new StringBuilder();
                for (String param : getAnonymousParameters(templatePlusArgs)) {
                    result.append(' ');
                    result.append(convertMarkupToText(param));
                }
                
                result.append(", ");
                return result.toString();
            } else if (action.equals("articles")) {
                StringBuilder result = new StringBuilder();
                for (String param : getAnonymousParameters(templatePlusArgs)) {
                    result.append(' ');
                    result.append("_wikiarticle_");
                    result.append(param.replaceAll(" ", "_"));
                }
                
                result.append(' ');
                return result.toString();
            } else {
                LOGGER.warn(String.format("Template '%s' has unknown action '%s'", template, action));
                return "";
            }
        }
    }

    public String convertMarkupToHTML(String text) {
        // See https://en.wikipedia.org/wiki/Help:Cheatsheet
        // TODO make it so
        return text;
    }
    
    public String convertMarkupToText(String text) {
        // See https://en.wikipedia.org/wiki/Help:Cheatsheet
        
        // TODO at least these
        // get rid of '{2,} pattern
        // convert {{!}} into | character
        // convert [] into a link
        //  [[File:Wiki.png|thumb|Caption]]
        //  [[red link articlename]]
        //  [[article#anchor]]
        //  [[article|anchor text]]
        // <ref name="LoC">...</ref>
        // What about HTML markup?
        
        return text;
    }
    
    private List<String> getAnonymousParameters(String templatePlusArgs) {
        List<String> result = new ArrayList<>();
        String[] pieces = templatePlusArgs.split("\\|");
        for (int i = 1; i < pieces.length; i++) {
            if (pieces[i].indexOf('=') == -1) {
                result.add(pieces[i]);
            }
        }
        
        return result;
    }

    private String getAction(String template) {
        String action = null;
        Pattern matchedPattern = null;
        for (int i = 0; i < _patterns.size(); i++) {
            Pattern curPattern = _patterns.get(i);
            if (curPattern.matcher(template).matches()) {
                if (matchedPattern != null) {
                    LOGGER.warn(String.format("'%s' matched by '%s' and '%s'", template, matchedPattern, curPattern));
                } else {
                    matchedPattern = curPattern;
                    action = _actions.get(i);
                }
            }
        }
        
        return action;
    }
}
