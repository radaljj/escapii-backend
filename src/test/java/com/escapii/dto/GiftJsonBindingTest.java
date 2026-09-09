package com.escapii.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Zakljucava vezivanje kljuca {@code isGift} iz JSON-a.
 *
 * <p>BookingRequest nosi {@code @JsonProperty("isGift")} uz komentar koji
 * objasnjava zasto: Lombok za {@code boolean isGift} pravi getter {@code isGift()},
 * a Jackson iz takvog gettera izvodi ime svojstva {@code "gift"} - skida "is"
 * prefiks. Bez anotacije bi kljuc koji forma salje bio ignorisan.
 *
 * <p>Komentar je do sada bio jedina zastita. Ko god sutra ukloni "suvisnu"
 * anotaciju ili prebaci DTO u record, srusi SVAKI poklon u produkciji dok svi
 * testovi ostaju zeleni - jer bi se zastavica tiho gasila u false i poklon bi se
 * ponasao kao obicna rezervacija. Otkrilo bi se tek kad kupac prijavi da je
 * iznenadjenje procurilo, a tada je nepovratno.
 */
class GiftJsonBindingTest {

    private final ObjectMapper om = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void kljucIsGiftIzFormeSeVezujeNaPolje() throws Exception {
        String json = """
            {"isGift": true,
             "giftRecipientName": "Ana Anić",
             "giftRecipientEmail": "ana@primer.rs"}
            """;

        BookingRequest r = om.readValue(json, BookingRequest.class);

        assertTrue(r.isGift(),
                "kljuc isGift nije stigao do polja. Najverovatnije je uklonjen "
              + "@JsonProperty(\"isGift\") - bez njega Jackson trazi kljuc \"gift\".");
        assertEquals("Ana Anić", r.getGiftRecipientName());
        assertEquals("ana@primer.rs", r.getGiftRecipientEmail());
    }

    /**
     * Zabelezeno jer je iznenadilo: kljuc {@code gift} TAKODJE vezuje.
     *
     * <p>{@code @JsonProperty} na polju DODAJE ime, ne zamenjuje ga - getter
     * {@code isGift()} i dalje doprinosi svoje izvedeno {@code "gift"}. Oba rade.
     *
     * <p>To ne cini anotaciju suvisnom, nego obrnuto: bez nje bi ostalo SAMO
     * {@code "gift"}, a forma salje {@code "isGift"}. Test iznad je taj koji stiti.
     * Ovaj postoji da sledeci koji ga procita ne pomisli da je nasao gresku.
     */
    @Test
    void kljucGiftJeDodatniAliasKojiTakodjeVezuje() throws Exception {
        BookingRequest r = om.readValue("{\"gift\": true}", BookingRequest.class);
        assertTrue(r.isGift());
    }

    /** Odsustvo kljuca je obicna rezervacija, ne greska. */
    @Test
    void bezKljucaJeObicnaRezervacija() throws Exception {
        BookingRequest r = om.readValue("{}", BookingRequest.class);
        assertFalse(r.isGift());
        assertNull(r.getGiftRecipientEmail());
    }
}
