package com.fanok.audiobooks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UrlTest {

    @Test
    public void hostOfParsesAndNormalises() {
        assertEquals("baza-knig.top", Url.hostOf("https://WWW.baza-knig.top/page/2/?q=%D0%B0"));
        assertEquals("", Url.hostOf("not a url"));
        assertEquals("", Url.hostOf(null));
        assertEquals("", Url.hostOf("/relative/path"));
    }

    @Test
    public void isHostComparesParsedHostsNotStrings() {
        assertTrue(Url.isHost("https://baza-knig.top/x/y", Url.SERVER_BAZA_KNIG));
        assertTrue(Url.isHost("https://www.knigoblud.club/", Url.SERVER_KNIGOBLUD));
        assertFalse(Url.isHost("https://baza-knig.top.evil.com/", Url.SERVER_BAZA_KNIG));
        assertFalse(Url.isHost("file:///sdcard/baza-knig.top", Url.SERVER_BAZA_KNIG));
    }

    @Test
    public void canonicalRewritesMovedHostsOnly() {
        assertEquals("https://baza-knig.top/kniga/1.html?x=1", Url.canonical("http://baza-knig.ink/kniga/1.html?x=1"));
        assertEquals("https://www.izib.uk/b/2", Url.canonical("https://www.IZIBUK.ru/b/2"));
        assertEquals("https://akniga.org/a", Url.canonical("https://akniga.org/a"));
        assertEquals("garbage", Url.canonical("garbage"));
    }
}
