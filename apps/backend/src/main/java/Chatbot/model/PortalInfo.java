package Chatbot.model;

public record PortalInfo(
        String url,
        String titleKa,
        String titleEn,
        String descriptionKa
) {
    public String getTitle(boolean isGeorgian) {
        return isGeorgian ? titleKa : titleEn;
    }
}