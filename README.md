# wikiwords

Tools and Hadoop workflows to generate data that maps from words to "concepts" (Wikipedia articles) and categories.

WikiDumpTool
------------

Counts of various page types, from running the tool on the most recent (as of 2015-11-12) English Wikipedia dump:

```
15/11/12 19:45:05 INFO tools.WikiDumpTool:184 - module-page: 2593
15/11/12 19:45:05 INFO tools.WikiDumpTool:184 - file-page: 901133
15/11/12 19:45:05 INFO tools.WikiDumpTool:184 - project-page: 937083
15/11/12 19:45:05 INFO tools.WikiDumpTool:184 - disambiguation-page: 257475
15/11/12 19:45:05 INFO tools.WikiDumpTool:184 - unknown-page: 192913
15/11/12 19:45:05 INFO tools.WikiDumpTool:184 - template-page: 640838
15/11/12 19:45:05 INFO tools.WikiDumpTool:184 - redirect-valid-page: 7053301
15/11/12 19:45:05 INFO tools.WikiDumpTool:184 - category-valid-page: 1320310
15/11/12 19:45:05 INFO tools.WikiDumpTool:184 - main-page: 4744786
```

All of the "unknonw-page" counts come from `Draft:`, `Portal:`, `Help:`, `MediaWiki:`, `Book:`, `TimedText:`, and `Topic:` pages.

WikiDumpTool
------------
