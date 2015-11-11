# wikiwords

Tools and Hadoop workflows to generate data that maps from words to "concepts" (Wikipedia articles) and categories.

WikiDumpTool
-----------

Counts of various page types, from running the tool on the most recent (as of 2015-11-08) English Wikipedia dump:

```
15/11/10 20:01:39 INFO tools.WikiDumpTool:167 - module-page: 2593
15/11/10 20:01:39 INFO tools.WikiDumpTool:167 - file-page: 901133
15/11/10 20:01:39 INFO tools.WikiDumpTool:167 - project-page: 937083
15/11/10 20:01:39 INFO tools.WikiDumpTool:167 - redirect-invalid-page: 2
15/11/10 20:01:39 INFO tools.WikiDumpTool:167 - unknown-page: 192913
15/11/10 20:01:39 INFO tools.WikiDumpTool:167 - template-page: 640838
15/11/10 20:01:39 INFO tools.WikiDumpTool:167 - redirect-valid-page: 7053299
15/11/10 20:01:39 INFO tools.WikiDumpTool:167 - category-valid-page: 1320310
15/11/10 20:01:39 INFO tools.WikiDumpTool:167 - main-page: 5002261
```
