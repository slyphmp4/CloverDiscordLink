package com.slyph.cloverdiscordlink.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextFormatter {

    private static final Pattern AMP_HEX = Pattern.compile("&(?:#)?([0-9A-Fa-f]{6})");
    private static final Pattern AMP_CODE = Pattern.compile("&([0-9A-FK-ORa-fk-or])");
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "black"),
            Map.entry('1', "dark_blue"),
            Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"),
            Map.entry('4', "dark_red"),
            Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"),
            Map.entry('7', "gray"),
            Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"),
            Map.entry('a', "green"),
            Map.entry('b', "aqua"),
            Map.entry('c', "red"),
            Map.entry('d', "light_purple"),
            Map.entry('e', "yellow"),
            Map.entry('f', "white"),
            Map.entry('k', "obfuscated"),
            Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"),
            Map.entry('n', "underlined"),
            Map.entry('o', "italic"),
            Map.entry('r', "reset")
    );

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();

    public Component deserialize(String template) {
        return deserialize(template, Map.of());
    }

    public Component deserialize(String template, Map<String, String> placeholders) {
        String normalized = normalize(template == null ? "" : template);
        List<TagResolver> resolvers = new ArrayList<>(placeholders.size());
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String token = "{" + entry.getKey() + "}";
            normalized = normalized.replace(token, "<" + entry.getKey() + ">");
            resolvers.add(Placeholder.unparsed(entry.getKey(), entry.getValue() == null ? "" : entry.getValue()));
        }
        return miniMessage.deserialize(normalized, resolvers.toArray(TagResolver[]::new));
    }

    public Component deserializeLines(List<String> lines, Map<String, String> placeholders) {
        return deserialize(String.join("\n", lines), placeholders);
    }

    public String plain(Component component) {
        return plainText.serialize(component);
    }

    String normalize(String input) {
        Matcher hex = AMP_HEX.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (hex.find()) {
            hex.appendReplacement(buffer, Matcher.quoteReplacement("<#" + hex.group(1) + ">"));
        }
        hex.appendTail(buffer);

        Matcher code = AMP_CODE.matcher(buffer.toString());
        buffer = new StringBuffer();
        while (code.find()) {
            String tag = LEGACY_TAGS.get(Character.toLowerCase(code.group(1).charAt(0)));
            code.appendReplacement(buffer, tag == null ? "" : Matcher.quoteReplacement("<" + tag + ">"));
        }
        code.appendTail(buffer);
        return buffer.toString();
    }
}
