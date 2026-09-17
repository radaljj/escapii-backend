package com.escapii.service.weather;

/**
 * Admin je uneo (ili promenio) destinaciju ili „Grad za prognozu" na rezervaciji.
 * {@link GeoWarmUpListener} odmah, u pozadini, geokodira upit i zapamti koordinate,
 * da ih jutarnji krug zatekne u kešu.
 *
 * @param weatherQuery tačno ono što bi otišlo geokoderu (weatherCity ako je unet, inače destinacija)
 */
public record DestinationAssignedEvent(String weatherQuery) {}
