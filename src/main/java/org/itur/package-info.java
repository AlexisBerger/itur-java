/**
 * Java implementation of ITU-R radiowave propagation recommendations for Earth-space links.
 *
 * <p>Package layout: {@code org.itur} (the {@link org.itur.Itur} facade), {@code org.itur.models}
 * (one class per recommendation), {@code org.itur.grid} (map grids and interpolation) and
 * {@code org.itur.util} (numerical and geodetic helpers).
 *
 * <p>Implemented recommendations (version in force noted in each class):
 * <ul>
 *   <li>{@link org.itur.models.P618} - ITU-R P.618-13: Earth-space propagation (rain, scintillation, XPD, diversity)</li>
 *   <li>{@link org.itur.models.P676} - ITU-R P.676-13: attenuation by atmospheric gases</li>
 *   <li>{@link org.itur.models.P838} - ITU-R P.838-3: specific attenuation by rain</li>
 *   <li>{@link org.itur.models.P839} - ITU-R P.839-4: rain height</li>
 *   <li>{@link org.itur.models.P837} - ITU-R P.837-7: precipitation characteristics</li>
 *   <li>{@link org.itur.models.P836} - ITU-R P.836-6: water vapour</li>
 *   <li>{@link org.itur.models.P840} - ITU-R P.840-7: clouds and fog</li>
 *   <li>{@link org.itur.models.P453} - ITU-R P.453-13: radio refractive index</li>
 *   <li>{@link org.itur.models.P1510} - ITU-R P.1510-1: mean surface temperature</li>
 *   <li>{@link org.itur.models.P1511} - ITU-R P.1511-1: topography</li>
 *   <li>{@link org.itur.models.P835} - ITU-R P.835-6 (reference standard atmosphere only; required by P.676)</li>
 * </ul>
 *
 * <p>{@link org.itur.Itur} combines them into the total atmospheric attenuation of a slant path.
 * Conventions: latitudes/longitudes in degrees (any longitude is accepted), frequencies in GHz,
 * lengths in km unless a parameter name says otherwise, probabilities {@code p} in percent of an
 * average year, arguments typed {@code Double} are optional ({@code null} = derive from the ITU maps).
 *
 * <p>Arguments without a physical meaning (NaN, a latitude beyond 90 degrees, a non-positive
 * frequency, an elevation outside ]0, 90] degrees for a slant path, a probability outside ]0, 100] %,
 * a negative rainfall rate, ...) are rejected with an {@link java.lang.IllegalArgumentException} that
 * names the argument and the accepted range. Arguments that are only outside the validity range of a
 * Recommendation are accepted (a warning is logged where the Recommendation states a limit). A
 * location that a map does not cover gives {@code NaN}, as in ITU-Rpy.
 */
package org.itur;
