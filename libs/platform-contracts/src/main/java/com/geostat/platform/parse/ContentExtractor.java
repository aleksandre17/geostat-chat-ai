package com.geostat.platform.parse;

/** Extracts structured document content from HTML using a parse profile. */
public interface ContentExtractor {

    CleanedDocument extract(HtmlPageInput page, ParseProfile profile);
}
