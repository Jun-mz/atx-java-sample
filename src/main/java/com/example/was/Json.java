package com.example.was;

/**
 * 예제에 외부 의존성을 들이지 않기 위한 최소한의 JSON 문자열 유틸.
 * 실제 서비스라면 Jackson/Gson 을 쓰는 게 맞다.
 */
public final class Json {

    private Json() {
    }

    /** JSON 문자열 리터럴 안에 넣을 수 있도록 이스케이프한다 (따옴표는 포함하지 않는다). */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
