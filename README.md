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

The syslog output file in EMR will end with a line like this:

`Articles processed: 4744786`

This is needed for input to the AnalyzeTermsTool

AnalyzeTermsTool
----------------

This tool takes the output of the `GenerateTermsTool`, and calculates the "best" topics (associated Wikipedia articles) for each unique word. By default this is limited to the top 20 articles for any given term.

The score for each term/article association is besed on the term frequency (TF), which is what percentage of all terms that were close enough to a given article link are this term, and inverse document frequency (IDF), which is the invervse of what percentage of all article links had this term as one of its "close terms". We actually use the Lucene TF*IDF scoring formula, which is `sqrt(TF) * (1 + log(total unique article links/(unique article links close to this term + 1))`
