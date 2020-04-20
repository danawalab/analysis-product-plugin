package com.danawa.util;

import java.io.Serializable;

public class CharVector implements CharSequence, Comparable<CharSequence>, Serializable {
    private static final long serialVersionUID = 1L;

    private char[] array;
    private int offset;
    private int length;
    protected int hash;

    private boolean isIgnoreCase;

    public CharVector() { }

    public CharVector(String str) {
        array = str.toCharArray();
        offset = 0;
        length = array.length;
    }

    public CharVector(char[] array) {
        this(array, 0, array.length);
    }

    public CharVector(char[] array, boolean isIgnoreCase) {
        this(array, 0, array.length, isIgnoreCase);
    }

    public CharVector(char[] array, int offset, int length) {
        this(array, offset, length, false);
    }

    public CharVector(char[] array, int offset, int length, boolean isIgnoreCase) {
        this.array = array;
        this.offset = offset;
        this.length = length;
        this.hash = 0;
        this.isIgnoreCase = isIgnoreCase;
    }

    public void init(char[] array, int offset, int length) {
        this.array = array;
        this.offset = offset;
        this.length = length;
        this.hash = 0;
    }

    public void init(int offset, int length) {
        this.offset = offset;
        this.length = length;
        this.hash = 0;
    }

    public void offset(int offset) {
        this.offset = offset;
        this.hash = 0;
    }

    public void length(int length) {
        this.length = length;
        this.hash = 0;
    }

    public void ignoreCase() {
        if (!isIgnoreCase) {
            this.isIgnoreCase = true;
            hash = 0;
        }
    }

    public void verifyCase() {
        if (isIgnoreCase) {
            this.isIgnoreCase = false;
            hash = 0;
        }
    }

    public boolean isIgnoreCase() {
        return isIgnoreCase;
    }

    // 해시코드는 대소문자 구분없이 모두 대문자 기준으로 만들어준다.
    public int hashCode() {
        if (hash > 0) {
            return hash;
        }
        int h = 0;
        int off = offset;
        for (int i = 0; i < length; i++) {
            int ch = array[off++];
            ch = toUpperChar(ch);
            h = 31 * h + ch;
        }
        hash = h;
        return h;
    }

    public CharVector trim() {
        while (length > 0 && array[offset] == ' ') {
            offset++;
            length--;
        }
        while (length > 0 && array[offset + length - 1] == ' ') {
            length--;
        }
        hash = 0;
        return this;
    }

    @Override
    public String toString() {
        if (length > 0) {
            return new String(array, offset, length);
        } else {
            return "";
        }
    }

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (anObject instanceof CharVector) {
            CharVector anotherArray = (CharVector) anObject;
            int n = length;
            if (n == anotherArray.length) {
                if (isIgnoreCase || anotherArray.isIgnoreCase) {
                    // 둘중 하나라도 ignorecase이면 ignorecase로 비교한다.
                    for (int i = 0; i < length; i++) {
                        if (toUpperChar(charAt(i)) != toUpperChar(anotherArray.charAt(i))) {
                            return false;
                        }
                    }
                } else {
                    for (int i = 0; i < length; i++) {
                        if (charAt(i) != anotherArray.charAt(i)) {
                            return false;
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    // share array reference
    @Override
    public CharVector clone() {
        CharVector cv = new CharVector(array, offset, length, isIgnoreCase);
        cv.hash = hash;
        return cv;
    }

    // public char[] copy(char[] buffer) {
    //     if (buffer == null) {
    //         buffer = new char[length];
    //     }
    //     System.arraycopy(array, offset, buffer, 0, length);
    //     return buffer;
    // }

    // public CharVector copy(CharVector cv) {
    //     cv.offset = offset;
    //     cv.length = length;
    //     cv.array = array;
    //     cv.hash = hash;
    //     cv.isIgnoreCase = isIgnoreCase;
    //     return cv;
    // }

    // public CharVector duplicate() {
    //     char[] buffer = new char[length];
    //     copy(buffer);
    //     return new CharVector(buffer, 0, length, isIgnoreCase);
    // }

    @Override
    public int compareTo(CharSequence cs) {
        int len1 = this.length;
        int len2 = cs.length();
        int minlen = len1;
        if (minlen > len2) {
            minlen = len2;
        }
        for (int cinx = 0; cinx < minlen; cinx++) {
            char c1 = this.charAt(cinx);
            char c2 = cs.charAt(cinx);
            if (c1 == c2) {
            } else if (c1 > c2) {
                return 1;
            } else if (c1 < c2) {
                return -1;
            }
        }
        if (len1 == len2) {
            return 0;
        } else if (len1 > len2) {
            return 1;
        } else if (len1 < len2) {
            return -1;
        }
        return 0;
    }

    // public int compareTo(char[] key, int offset, int length) {
    //     int len1 = this.length;
    //     int len2 = length;
    //     int len = len1 < len2 ? len1 : len2;
    //     for (int i = 0; i < len; i++) {
    //         char ch = charAt(i);
    //         if (ch != key[offset + i]) {
    //             return ch - key[offset + i];
    //         }
    //     }
    //     return len1 - len2;
    // }

    @Override
    public char charAt(int inx) {
        char ch = array[offset + inx];
        if (isIgnoreCase) {
            if ((ch <= 'z' && ch >= 'a')) { // 소문자이면..
                ch -= 32;
            }
        }
        return ch;
    }

    private char toUpperChar(int ch) {
        if ((ch <= 'z' && ch >= 'a')) { // 소문자이면..
            ch -= 32;
        }
        return (char) ch;
    }

    // public void setChar(int inx, char ch) {
    //     array[offset + inx] = ch;
    //     hash = 0;
    // }

    // // 내부 공백을 삭제해준다.
    // public CharVector removeWhitespaces() {
    //     int len = 0;
    //     for (int i = 0; i < length; i++) {
    //         if (array[offset + i] != ' ') {
    //             array[offset + len++] = array[offset + i];
    //         }
    //     }
    //     length = len;
    //     hash = 0;
    //     return this;
    // }

    // public boolean hasWhitespaces() {
    //     for (int i = 0; i < length; i++) {
    //         if (array[offset + i] == ' ') {
    //             return true;
    //         }
    //     }
    //     return false;
    // }

    public char[] array() {
        return array;
    }

    public int offset() {
        return offset;
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public CharSequence subSequence(int startIndex, int endIndex) {
        CharVector cv = new CharVector();
        cv.array = this.array;
        cv.offset = this.offset + startIndex;
        cv.length = endIndex - startIndex + 1;
        return cv;
    }

    // public Reader getReader() {
    //     return new CharArrayReader(array, offset, length);
    // }
}