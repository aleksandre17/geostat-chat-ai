package com.geostat.ingestion.parse.reparse;

public class CorpusReparseAlreadyRunningException extends RuntimeException {

    public CorpusReparseAlreadyRunningException(String corpusName) {
        super("reparse already running for corpus: " + corpusName);
    }
}
