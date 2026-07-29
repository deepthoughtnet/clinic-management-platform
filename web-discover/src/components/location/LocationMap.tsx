import { type ReactNode, useEffect, useMemo, useState } from "react";
import { divIcon } from "leaflet";
import { MapContainer, Marker, TileLayer, useMap, useMapEvents } from "react-leaflet";
import { discoverConfig } from "../../config";
import type { LocationCoordinates } from "./locationTypes";

const markerIcon = divIcon({
  className: "jeevanam-location-marker",
  html: '<span class="jeevanam-location-marker__pin" aria-hidden="true"><span class="jeevanam-location-marker__core"></span></span>',
  iconSize: [30, 40],
  iconAnchor: [15, 40],
  popupAnchor: [0, -38],
});

function MapSynchronizer({
  center,
  zoom,
  onMapClick,
}: {
  center: LocationCoordinates;
  zoom: number;
  onMapClick?: (coordinates: LocationCoordinates) => void;
}) {
  const map = useMap();
  useEffect(() => {
    map.setView([center.latitude, center.longitude], zoom, { animate: false });
    const frame = window.requestAnimationFrame(() => map.invalidateSize());
    return () => window.cancelAnimationFrame(frame);
  }, [center.latitude, center.longitude, map, zoom]);

  useEffect(() => {
    const handleResize = () => map.invalidateSize();
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, [map]);

  useMapEvents(onMapClick ? {
    click(event) {
      onMapClick({
        latitude: event.latlng.lat,
        longitude: event.latlng.lng,
      });
    },
  } : {});

  return null;
}

export type LocationMapProps = {
  center: LocationCoordinates | null;
  zoom?: number;
  marker?: LocationCoordinates | null;
  interactive?: boolean;
  loading?: boolean;
  ariaLabel: string;
  className?: string;
  emptyState: ReactNode;
  unavailableState?: ReactNode;
  useDefaultCenter?: boolean;
  onMapClick?: (coordinates: LocationCoordinates) => void;
  onMarkerDragEnd?: (coordinates: LocationCoordinates) => void;
};

export function LocationMap({
  center,
  zoom = discoverConfig.mapDefaultZoom,
  marker,
  interactive = false,
  loading = false,
  ariaLabel,
  className,
  emptyState,
  unavailableState,
  useDefaultCenter = true,
  onMapClick,
  onMarkerDragEnd,
}: LocationMapProps) {
  const tileUrl = discoverConfig.mapTileUrl.trim();
  const tileAttribution = discoverConfig.mapTileAttribution.trim();
  const mapCenter = center ?? (useDefaultCenter && discoverConfig.mapDefaultLatitude != null && discoverConfig.mapDefaultLongitude != null
    ? {
        latitude: discoverConfig.mapDefaultLatitude,
        longitude: discoverConfig.mapDefaultLongitude,
      }
    : null);

  const markerPosition = useMemo<LocationCoordinates | null>(() => marker ?? null, [marker]);
  const canRenderMap = Boolean(mapCenter && tileUrl && tileAttribution);
  const renderableMapCenter = canRenderMap ? mapCenter : null;
  const [tileStatus, setTileStatus] = useState<"loading" | "ready" | "error">(
    canRenderMap ? "loading" : "error",
  );

  useEffect(() => {
    setTileStatus(canRenderMap ? "loading" : "error");
  }, [canRenderMap, mapCenter?.latitude, mapCenter?.longitude, tileUrl, tileAttribution, zoom]);

  const showLoading = canRenderMap && (loading || tileStatus === "loading");
  const showEmptyState = !canRenderMap;
  const showUnavailableState = canRenderMap && tileStatus === "error";

  return (
    <div className={`location-map jeevanam-location-map ${className ?? ""}`.trim()} aria-label={ariaLabel}>
      <div className="location-map__canvas-shell">
        {renderableMapCenter ? (
          <MapContainer
            center={[renderableMapCenter.latitude, renderableMapCenter.longitude]}
            zoom={zoom}
            className="location-map__canvas jeevanam-location-map__canvas"
            scrollWheelZoom={false}
            dragging={interactive}
            doubleClickZoom={interactive}
            touchZoom={interactive}
            zoomControl
            attributionControl
          >
            <TileLayer
              url={tileUrl}
              attribution={tileAttribution}
              eventHandlers={{
                load() {
                  setTileStatus("ready");
                },
                tileerror() {
                  setTileStatus("error");
                },
              }}
            />
            <MapSynchronizer center={renderableMapCenter} zoom={zoom} onMapClick={interactive ? onMapClick : undefined} />
            {markerPosition ? (
              <Marker
                position={[markerPosition.latitude, markerPosition.longitude]}
                icon={markerIcon}
                draggable={interactive}
                eventHandlers={
                  interactive && onMarkerDragEnd
                    ? {
                        dragend(event) {
                          const markerLayer = event.target;
                          const coordinates = markerLayer.getLatLng();
                          onMarkerDragEnd({
                            latitude: coordinates.lat,
                            longitude: coordinates.lng,
                          });
                        },
                      }
                    : undefined
                }
              />
            ) : null}
          </MapContainer>
        ) : null}
        {showEmptyState ? (
          <div className="location-map__fallback location-map-fallback" aria-hidden="true">
            {emptyState}
          </div>
        ) : null}
        {showUnavailableState ? (
          <div className="location-map__fallback location-map-fallback" aria-hidden="true">
            {unavailableState ?? emptyState}
          </div>
        ) : null}
        {showLoading ? (
          <div className="location-map__status-overlay jeevanam-location-map__loading" aria-live="polite">
            Loading map…
          </div>
        ) : null}
      </div>
    </div>
  );
}
