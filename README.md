# wikiwords

Tools and Hadoop workflows to generate data that maps from words to "concepts" (Wikipedia articles) and categories.

WikiDumpTool
-----------

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

GenerateTermsTool
----------------

This tool takes the output of the `WikiDumpTool`, creates HTML using the [info.bliki.wiki](https://bitbucket.org/axelclk/info.bliki.wiki/wiki/Home) project, parses the results with [Tika](http://tika.apache.org), and then creates output records for terms that are "close to" Wikipedia article links.

A typical invocation looks like:

`hadoop jar wikiwords-job-1.0-SNAPSHOT.jar com.scaleunlimited.wikiwords.tools.GenerateTermsTool -inputdir s3n://su-wikidump/wikidump-20151112/data/ -maxdistance 10 -workingdir /working1`

This command assumes that you've got the results of the `WikiDumpTool` uploaded to the `su-wikidump` bucket in Amazon's S3, adn that you've previously created a `/working` directory in HDFS.

Using a very small EMR cluster of 2 m3.xlarge slaves, it took 1 hour for the above command to process a full English Wikipedia dump (4.7 million pages), and it generated 1.2 billion term/article associations.
