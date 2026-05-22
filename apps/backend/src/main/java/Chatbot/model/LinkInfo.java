package Chatbot.model;

public record LinkInfo(
        String url,
        String titleKa,
        String titleEn
) {
    public String getTitle(boolean isGeorgian) {
        return isGeorgian ? titleKa : titleEn;
    }
}