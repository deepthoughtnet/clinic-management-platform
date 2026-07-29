import { useEffect, useState } from "react";
import type { LocationMapEntry, LocationCoordinates } from "./locationTypes";
import { buildDirectionsUrl, locationAddressLine, locationCoordinates, locationDisplayLabel } from "./locationHelpers";
import { LocationMap } from "./LocationMap";

const EMPTY_LOCATIONS: LocationMapEntry[] = [];

export type LocationDisplayMapProps = {
  providerName: string;
  locations?: LocationMapEntry[];
  className?: string;
  compact?: boolean;
  title?: string;
  directionsLabel?: string;
};

export function LocationDisplayMap({
  providerName,
  locations,
  className,
  compact = false,
  title = "Location",
  directionsLabel = "Get directions",
}: LocationDisplayMapProps) {
  const locationList = locations ?? EMPTY_LOCATIONS;
  const [activeIndex, setActiveIndex] = useState(() => {
    const withCoordinatesIndex = locationList.findIndex((location) => location.latitude != null && location.longitude != null);
    return withCoordinatesIndex >= 0 ? withCoordinatesIndex : 0;
  });

  useEffect(() => {
    if (!locationList.length) {
      if (activeIndex !== 0) {
        setActiveIndex(0);
      }
      return;
    }
    const activeLocation = locationList[activeIndex];
    if (!activeLocation || (activeLocation.latitude == null && activeLocation.longitude == null)) {
      const withCoordinatesIndex = locationList.findIndex((location) => location.latitude != null && location.longitude != null);
      setActiveIndex(withCoordinatesIndex >= 0 ? withCoordinatesIndex : 0);
    }
  }, [activeIndex, locationList]);

  const activeLocation = locationList[activeIndex] ?? locationList[0] ?? null;
  const coordinates = locationCoordinates(activeLocation ?? undefined);
  const mapCenter = coordinates;
  const directionsUrl = buildDirectionsUrl(activeLocation);
  const locationLabel = locationDisplayLabel(activeLocation, providerName);
  const address = locationAddressLine(activeLocation);

  return (
    <section className={`location-display-map ${compact ? "is-compact" : ""} ${className ?? ""}`.trim()} aria-label={`${providerName} location`}>
      <div className="location-display-map__heading">
        <strong>{title}</strong>
        <span>{locationLabel}</span>
      </div>
      <LocationMap
        center={mapCenter}
        marker={coordinates}
        ariaLabel={`${providerName} location map`}
        className="location-display-map__map"
        useDefaultCenter={false}
        emptyState={(
          <div className="location-map__fallback-copy">
            <strong>Map location has not been pinned yet.</strong>
          </div>
        )}
        unavailableState={(
          <div className="location-map__fallback location-map-fallback" aria-hidden="true">
            <strong>Map is temporarily unavailable.</strong>
            <span>The address is still shown below.</span>
          </div>
        )}
      />
      <div className="location-display-map__details">
        <p>{address || "Address shared after publication."}</p>
        {locationList.length > 1 ? (
          <div className="location-branch-list" role="tablist" aria-label={`${providerName} branches`}>
            {locationList.map((location, index) => {
              const label = locationDisplayLabel(location, `Branch ${index + 1}`);
              return (
                <button
                  key={`${label}-${index}`}
                  type="button"
                  className={`chip-button ${index === activeIndex ? "is-active" : ""}`}
                  onClick={() => setActiveIndex(index)}
                  role="tab"
                  aria-selected={index === activeIndex}
                >
                  {label}
                </button>
              );
            })}
          </div>
        ) : null}
        <div className="cta-row">
          {directionsUrl ? (
            <a className="secondary-button" href={directionsUrl} target="_blank" rel="noreferrer">
              {directionsLabel}
            </a>
          ) : null}
        </div>
      </div>
    </section>
  );
}
