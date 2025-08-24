package com.nnpg.glazed.utils.glazed;

import org.jetbrains.annotations.NotNull;

import java.nio.CharBuffer;
import java.security.SecureRandom;
import java.util.Arrays;

public class EncryptedString implements AutoCloseable, CharSequence {
    private final char[] key;
    private final char[] value;
    private final int length;
    private static final SecureRandom random;
    private boolean closed;

    public EncryptedString(String string) {
        this.closed = false;
        if (string == null) {
            throw new IllegalArgumentException("Input string cannot be null");
        }
        this.length = string.length();
        this.key = generateRandomKey(Math.min(this.length, 128));
        this.value = new char[this.length];
        string.getChars(0, this.length, this.value, 0);
        applyXorEncryption(this.value, this.key, 0, this.length);
    }

    public EncryptedString(final char[] original, final char[] original2) {
        this.closed = false;
        if (original == null || original2 == null) {
            throw new IllegalArgumentException("Neither encrypted value nor key can be null");
        }
        if (original2.length == 0) {
            throw new IllegalArgumentException("Encryption key cannot be empty");
        }
        this.length = original.length;
        this.value = Arrays.copyOf(original, original.length);
        this.key = Arrays.copyOf(original2, original2.length);
    }

    public static EncryptedString of(final String s) {
        return new EncryptedString(s);
    }

    public static EncryptedString of(final String s, final String s2) {
        if (s == null || s2 == null) {
            throw new IllegalArgumentException("Neither encrypted data nor key can be null");
        }
        return new EncryptedString(s.toCharArray(), s2.toCharArray());
    }

    private static char[] generateRandomKey(final int n) {
        final char[] array = new char[n];
        for (int i = 0; i < n; ++i) {
            array[i] = (char) EncryptedString.random.nextInt(65536);
        }
        return array;
    }

    private static void applyXorEncryption(final char[] array, final char[] array2, final int n, final int n2) {
        for (int i = 0; i < n2; ++i) {
            final int n3 = n + i;
            array[n3] ^= array2[i % array2.length];
        }
    }

    @Override
    public int length() {
        this.setClosed();
        return this.length;
    }

    @Override
    public char charAt(final int n) {
        this.setClosed();
        if (n < 0 || n >= this.length) {
            throw new IndexOutOfBoundsException("Index: " + n + ", Length: " + this.length);
        }
        return (char) (this.value[n] ^ this.key[n % this.key.length]);
    }

    @NotNull
    @Override
    public CharSequence subSequence(final int n, final int n2) {
        this.setClosed();
        if (n < 0 || n2 > this.length || n > n2) {
            throw new IndexOutOfBoundsException("Invalid subsequence range: " + n + " to " + n2 + " (length: " + this.length);
        }
        final int n3 = n2 - n;
        final char[] array = new char[n3];
        System.arraycopy(this.value, n, array, 0, n3);
        final char[] array2 = new char[n3];
        for (int i = 0; i < n3; ++i) {
            array2[i] = this.key[(n + i) % this.key.length];
        }
        applyXorEncryption(array, this.key, 0, n3);
        applyXorEncryption(array, array2, 0, n3);
        return new EncryptedString(array, array2);
    }

    @NotNull
    @Override
    public String toString() {
        this.setClosed();
        final char[] array = new char[this.length];
        for (int i = 0; i < this.length; ++i) {
            array[i] = this.charAt(i);
        }
        final String s = new String(array);
        Arrays.fill(array, '\0');
        return s;
    }

    @NotNull
    public String a() {
        this.setClosed();
        return this.toString();
    }

    public CharBuffer b() {
        this.setClosed();
        final CharBuffer allocate = CharBuffer.allocate(this.length);
        for (int i = 0; i < this.length; ++i) {
            allocate.put(i, this.charAt(i));
        }
        allocate.flip();
        return allocate.asReadOnlyBuffer();
    }

    @Override
    public void close() {
        if (!this.closed) {
            Arrays.fill(this.value, '\0');
            Arrays.fill(this.key, '\0');
            this.closed = true;
        }
    }

    private void setClosed() {
        if (this.closed) {
            throw new IllegalStateException("This EncryptedString has been closed and cannot be used");
        }
    }

    @Override
    public boolean equals(final Object o) {
        this.setClosed();
        if (this == o) {
            return true;
        }
        if (!(o instanceof CharSequence)) {
            return false;
        }
        if (this.length != ((CharSequence) o).length()) {
            return false;
        }
        for (int i = 0; i < this.length; ++i) {
            if (this.charAt(i) != ((CharSequence) o).charAt(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        this.setClosed();
        int n = 0;
        for (int i = 0; i < this.length; ++i) {
            n = 31 * n + this.charAt(i);
        }
        return n;
    }

    static {
        random = new SecureRandom();
    }
}
