package com.nnpg.glazed.utils.glazed;

public class StringUtils {
    public static String convertUnicodeToAscii(String text) {
        StringBuilder result = new StringBuilder();

        for (char c : text.toCharArray()) {
            switch (c) {
                case 'ᴀ', 'а', 'α', 'ａ', 'А', 'Α', 'Ａ' -> result.append('a');
                case 'ʙ', 'в', 'β', 'ｂ', 'В', 'Β', 'Ｂ' -> result.append('b');
                case 'ᴄ', 'с', 'ｃ', 'С', 'Ｃ' -> result.append('c');
                case 'ᴅ', 'ｄ', 'Ｄ' -> result.append('d');
                case 'ᴇ', 'е', 'ε', 'ｅ', 'Е', 'Ε', 'Ｅ' -> result.append('e');
                case 'ꜰ', 'ｆ', 'Ｆ' -> result.append('f');
                case 'ɢ', 'ｇ', 'Ｇ' -> result.append('g');
                case 'ʜ', 'н', 'ｈ', 'Н', 'Η', 'Ｈ' -> result.append('h');
                case 'ɪ', 'і', 'ｉ', 'І', 'Ι', 'Ｉ' -> result.append('i');
                case 'ᴊ', 'ј', 'ｊ', 'Ј', 'Ｊ' -> result.append('j');
                case 'ᴋ', 'к', 'κ', 'ｋ', 'К', 'Κ', 'Ｋ' -> result.append('k');
                case 'ʟ', 'ｌ', 'Ｌ' -> result.append('l');
                case 'ᴍ', 'м', 'ｍ', 'М', 'Μ', 'Ｍ' -> result.append('m');
                case 'ɴ', 'п', 'η', 'ｎ', 'П', 'Ｎ', 'Ν' -> result.append('n');
                case 'ᴏ', 'о', 'ο', 'ｏ', 'О', 'Ο', 'Ｏ' -> result.append('o');
                case 'ᴘ', 'р', 'ρ', 'ｐ', 'Р', 'Ρ', 'Ｐ' -> result.append('p');
                case 'ꞯ', 'ǫ', 'ｑ', 'Ｑ' -> result.append('q');
                case 'ʀ', 'ｒ', 'Ｒ' -> result.append('r');
                case 'ꜱ', 'ѕ', 'ｓ', 'Ѕ', 'Ｓ' -> result.append('s');
                case 'ᴛ', 'т', 'τ', 'ｔ', 'Т', 'Τ', 'Ｔ' -> result.append('t');
                case 'ᴜ', 'υ', 'ｕ', 'Ｕ' -> result.append('u');
                case 'ᴠ', 'ν', 'ｖ', 'Ｖ' -> result.append('v');
                case 'ᴡ', 'ω', 'ｗ', 'Ｗ' -> result.append('w');
                case 'x', 'х', 'χ', 'ｘ', 'Х', 'Χ', 'Ｘ' -> result.append('x');
                case 'ʏ', 'у', 'Υ', 'ｙ', 'У', 'Ｙ' -> result.append('y');
                case 'ᴢ', 'ｚ', 'Ζ', 'Ｚ' -> result.append('z');

                // Numbers
                case '０' -> result.append('0');
                case '１' -> result.append('1');
                case '２' -> result.append('2');
                case '３' -> result.append('3');
                case '４' -> result.append('4');
                case '５' -> result.append('5');
                case '６' -> result.append('6');
                case '７' -> result.append('7');
                case '８' -> result.append('8');
                case '９' -> result.append('9');

                // Special characters
                case '　' -> result.append(' '); // Full-width space
                case '－' -> result.append('-');
                case '＿' -> result.append('_');
                case '．' -> result.append('.');
                case '，' -> result.append(',');
                case '！' -> result.append('!');
                case '？' -> result.append('?');
                case '：' -> result.append(':');
                case '；' -> result.append(';');
                case '（' -> result.append('(');
                case '）' -> result.append(')');
                case '［' -> result.append('[');
                case '］' -> result.append(']');
                case '｛' -> result.append('{');
                case '｝' -> result.append('}');
                case '＋' -> result.append('+');
                case '＝' -> result.append('=');
                case '＜' -> result.append('<');
                case '＞' -> result.append('>');
                case '＆' -> result.append('&');
                case '％' -> result.append('%');
                case '＄' -> result.append('$');
                case '＃' -> result.append('#');
                case '＠' -> result.append('@');
                case '＊' -> result.append('*');
                case '／' -> result.append('/');
                case '＼' -> result.append('\\');
                case '｜' -> result.append('|');
                case '～' -> result.append('~');
                case '｀' -> result.append('`');
                case '＾' -> result.append('^');
                case '″' -> result.append('"');
                case '＇' -> result.append('\'');

                default -> result.append(Character.toLowerCase(c));
            }
        }

        return result.toString().toLowerCase();
    }
}
