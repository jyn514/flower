<?xml version="1.0" encoding="utf-8"?>
<!-- https://darekkay.com/blog/rss-styling/ -->
<xsl:stylesheet version="3.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:atom="http://www.w3.org/2005/Atom">
  <xsl:output method="html" version="1.0" encoding="UTF-8" indent="yes"/>
  <xsl:template match="/">
    <html xmlns="http://www.w3.org/1999/xhtml" lang="en">
      <head>
        <title>
          RSS Feed | <xsl:value-of select="/atom:feed/atom:title"/>
        </title>
        <link rel="stylesheet" href="/minima.css" />
      </head>
      <body>
        <main class="page-content" aria-label="Content">
          <div class="wrapper">
            <p>
              This is an RSS feed. Visit
              <a href="https://aboutfeeds.com">About Feeds</a>
              to learn more and get started.
            </p>
            <p>
              this page is not really meant to be viewed standalone, although i've slapped some basic styling on it.
              go <a href="/">home</a> to see the intended styling.
            </p>
            <h1>Recent blog posts</h1>
            <nav class="home">
              <ul class="post-list">
                <xsl:for-each select="/atom:feed/atom:entry">
                  <span class="post-meta">
                    <xsl:value-of select="atom:subtitle"/>
                    Last updated:
                    <xsl:value-of select="substring(atom:updated, 0, 11)" />
                  </span>
                  <li><a class="post-link">
                    <xsl:attribute name="href">
                      <xsl:value-of select="atom:link/@href"/>
                    </xsl:attribute>
                    <xsl:value-of select="atom:title"/>
                  </a></li>
                </xsl:for-each>
              </ul></nav>
          </div></main>
      </body>
    </html>
  </xsl:template>
</xsl:stylesheet>
