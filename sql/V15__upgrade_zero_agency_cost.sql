-- Poslovno pravilo: ACCOMMODATION_UPGRADE (Superior/Premium) je fiksan fee/osoba
-- koji predstavlja cistu 50/50 zaradu Escapii-ja i agencije. Agencija tu nema
-- dodatan trosak - rezervise hotel po dogovorenoj base ceni koju admin unosi
-- u BASE_PACKAGE.
--
-- Zato: za sve postojece rezervacije gde upgrade postoji a agency_cost je NULL
-- (nikad nije unet) postavi na 0. Rezervacije gde je admin vec uneo neki broj
-- (npr. za test) se NE diraju - trebalo bi rucno proveriti/postaviti na 0 ako je
-- to zeljeni model.
UPDATE booking_financial_items
   SET agency_cost = 0
 WHERE item_type = 'ACCOMMODATION_UPGRADE'
   AND agency_cost IS NULL;
