package com.geostat.platform.parse;

/** Removes boilerplate paragraphs from extracted body text. */
public interface BoilerplateStripper {

    String stripFromBody(String text, ParseProfile profile);

    boolean isBoilerplateParagraph(String paragraph, ParseProfile profile);
}
