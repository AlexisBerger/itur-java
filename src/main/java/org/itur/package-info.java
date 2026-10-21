/**
 * Java implementation of ITU-R radiowave propagation recommendations for Earth-space links.
 *
 * <p>Implemented recommendations (version in force noted in each class):
 * <ul>
 *   <li>{@link org.itur.P618} - ITU-R P.618-13: Earth-space propagation (rain, scintillation, XPD, diversity)</li>
 *   <li>{@link org.itur.P676} - ITU-R P.676-11: attenuation by atmospheric gases</li>
 *   <li>{@link org.itur.P838} - ITU-R P.838-3: specific attenuation by rain</li>
 *   <li>{@link org.itur.P839} - ITU-R P.839-4: rain height</li>
 *   <li>{@link org.itur.P837} - ITU-R P.837-7: precipitation characteristics</li>
 *   <li>{@link org.itur.P836} - ITU-R P.836-6: water vapour</li>
 *   <li>{@link org.itur.P840} - ITU-R P.840-7: clouds and fog</li>
 *   <li>{@link org.itur.P453} - ITU-R P.453-13: radio refractive index</li>
 *   <li>{@link org.itur.P1510} - ITU-R P.1510-1: mean surface temperature</li>
 *   <li>{@link org.itur.P1511} - ITU-R P.1511-1: topography</li>
 *   <li>{@link org.itur.P835} - ITU-R P.835-6 (reference standard atmosphere only; required by P.676)</li>
 * </ul>
 *
 * <p>{@link org.itur.Itur} combines them into the total atmospheric attenuation of a slant path.
 * Conventions: latitudes/longitudes in degrees (any longitude is accepted), frequencies in GHz,
 * lengths in km unless a parameter name says otherwise, probabilities {@code p} in percent of an
 * average year, arguments typed {@code Double} are optional ({@code null} = derive from the ITU maps).
 */
package org.itur;
