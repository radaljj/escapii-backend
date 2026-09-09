package com.escapii.service;

/**
 * Vokativ imena, za obraćanje u mejlu: "Zdravo, Uroše," a ne "Zdravo, Uroš,".
 *
 * <p>Srpski menja ime kad mu se obraćaš, i to ne po pravilu koje se može
 * napisati u tri reda: Uroš→Uroše, Petar→Petre, Miloš→Miloše, ali Marko→Marko,
 * Nikola→Nikola, i gotovo sva ženska imena ostaju ista. Zato se pita servis koji
 * ima rečnik, a ne izmišlja pravilo.
 *
 * <p>Ugovor koji svaki pozivalac sme da pretpostavi: <b>nikad ne baca i nikad ne
 * vraća prazno.</b> Nepoznato ime, isključen servis, istekao rok, mreža — sve
 * to vraća nominativ, ono što je kupac otkucao. "Zdravo, Uroš," je manja šteta
 * od mejla koji nije otišao.
 */
public interface VocativeService {

    /**
     * @param firstName ime kako je uneto; sme biti null, prazno, sa razmacima,
     *                  malim slovima, ili "Ime Prezime" — uzima se samo prvo ime
     * @return vokativ tog imena, ili isto ime (normalizovano) ako se ne zna
     */
    String vocative(String firstName);
}
