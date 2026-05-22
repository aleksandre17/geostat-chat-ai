package Chatbot.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Handles small talk and portal list query detection.
 * Returns null when the message is not small talk.
 */
@Component
public class SmallTalkHandler {

    public String handle(String message, boolean isGeorgian) {
        String lower = message.toLowerCase();

        if (containsAny(lower, "გამარჯობა", "სალამი", "hello", "hi", "hey", "გაუმარჯოს", "მოგესალმები")
                && message.length() < 40) {
            return isGeorgian
                    ? "გამარჯობა. მე საქსტატის ვირტუალური ასისტენტი ვარ. რაში შემიძლია დაგეხმაროთ?"
                    : "Hello. I'm GeoStat's virtual assistant. How can I help you?";
        }
        if (containsAny(lower, "მადლობა", "გმადლობთ", "thank", "thanks", "დიდი მადლობა")
                && message.length() < 40) {
            return isGeorgian
                    ? "არაფრის. თუ სხვა რამეში დაგჭირდებათ დახმარება, მითხარით."
                    : "You're welcome. Let me know if you need anything else.";
        }
        if (containsAny(lower, "როგორ ხარ", "რა ხდება", "how are you", "what's up", "რას აკეთებ")) {
            return isGeorgian
                    ? "კარგად, მადლობა. რაში შემიძლია დაგეხმაროთ?"
                    : "Doing well, thanks. What can I help you with?";
        }
        if (containsAny(lower, "ვინ ხარ", "რა ხარ", "who are you", "what are you", "რა შეგიძლია", "what can you do", "რაში მეხმარები")) {
            return isGeorgian
                    ? "მე საქსტატის ვირტუალური ასისტენტი ვარ. შემიძლია დაგეხმაროთ სტატისტიკური ინფორმაციის მოძიებაში — მოსახლეობა, ეკონომიკა, დასაქმება, ვაჭრობა და სხვა."
                    : "I'm GeoStat's virtual assistant. I can help you find statistical information — population, economy, employment, trade, and more.";
        }
        if (containsAny(lower, "ვინ შეგქმნა", "ვინ გაკეთა", "ვინ დაგწერა", "ვინ მიერ ხარ შექმნილი",
                "ვინ შექმნა", "ვინ აგაწყო", "შემქმნელ", "დეველოპერ",
                "who created", "who made you", "who built you")) {
            return isGeorgian
                    ? "მე შევიქმენი საქსტატში (საქართველოს სტატისტიკის ეროვნული სამსახური). მთავარი დეველოპერი — გუგა გოგუა (https://www.linkedin.com/in/guga-gogua-418a902a2/)."
                    : "I was created at GeoStat (National Statistics Office of Georgia). Lead developer — Guga Gogua (https://www.linkedin.com/in/guga-gogua-418a902a2/).";
        }
        if (containsAny(lower, "ნახვამდის", "მშვიდობით") ||
                (containsAny(lower, "bye", "goodbye", "see you") && message.length() < 30)) {
            return isGeorgian ? "ნახვამდის. წარმატებები." : "Goodbye. Take care.";
        }
        if (containsAny(lower, "დამეხმარე", "help", "დახმარება", "არ ვიცი", "რა ვკითხო")) {
            return isGeorgian
                    ? "შეგიძლიათ იკითხოთ მაგალითად: მოსახლეობის სტატისტიკა, ინფლაციის მონაცემები, დასაქმება, ტურიზმი, საგარეო ვაჭრობა."
                    : "You can ask about: population statistics, inflation data, employment, tourism, external trade.";
        }
        return null;
    }

    /**
     * Returns a clarification question when the user's query could not be matched
     * to any GeoStat topic or resource. Guides the user to rephrase with examples.
     */
    public String clarificationRequest(boolean isGeorgian) {
        return isGeorgian
                ? "ვერ დავადგინე, კონკრეტულად რა გაინტერესებთ. გთხოვთ, გადაუფორმეთ კითხვა ან დააკონკრეტეთ - მაგალითად: \"მოსახლეობა\", \"ინფლაცია\", \"დასაქმება\", \"ვაჭრობა\", \"ტურიზმი\"."
                : "I wasn't able to identify what you're looking for. Could you clarify or rephrase? For example: \"population\", \"inflation\", \"employment\", \"trade\", \"tourism\".";
    }

    public boolean isPortalListQuery(String lowerQuery) {
        boolean hasPortalKw  = containsAny(lowerQuery, "პორტალ", "portal", "portals");
        boolean hasCalcKw    = containsAny(lowerQuery, "კალკულატორებ", "calculators", "ინტერაქტიულ ინსტრუმენტ", "interactive tool");
        boolean isListReq    = containsAny(lowerQuery, "რა პორტალ", "all portal", "რა კალკულატორ");
        boolean isSpecific   = containsAny(lowerQuery, "cpi", "სამომხმარებლო", "ინდექსაცი", "პერსონალურ ინფლაცი", "გადახდ", "გადასახად", "mytaxes", "ავტომობილ", "მანქან", "ბავშვ", "მოზარდ", "youth");
        if (isSpecific) return false;
        boolean isShortGeneric = (hasPortalKw || hasCalcKw) && lowerQuery.length() < 35;
        return isListReq || isShortGeneric;
    }

    /**
     * Matches keywords against text.
     * - Latin/ASCII keywords: requires word boundaries (\b) to prevent
     *   substring false positives (e.g. "hi" inside "history").
     * - Georgian keywords: falls back to substring match since Georgian
     *   characters are not \w chars and \b boundaries don't apply.
     */
    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            String kwLower = kw.toLowerCase();
            boolean isLatin = kwLower.chars().allMatch(c -> c < 128);
            if (isLatin) {
                if (Pattern.compile("\\b" + Pattern.quote(kwLower) + "\\b").matcher(text).find()) return true;
            } else {
                if (text.contains(kwLower)) return true;
            }
        }
        return false;
    }
}