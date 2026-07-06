import { Avatar, type AvatarProps } from "@mui/material";

import { useAuthenticatedImage } from "../../hooks/useAuthenticatedImage";

type DoctorAvatarProps = Omit<AvatarProps, "src" | "children"> & {
  name: string | null | undefined;
  photoUrl?: string | null | undefined;
};

function initials(name: string | null | undefined) {
  const source = name?.trim() || "Doctor";
  const cleaned = source.replace(/^dr\.?\s+/i, "").trim();
  const parts = cleaned.split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return "DR";
  }
  return parts
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("");
}

export default function DoctorAvatar({ name, photoUrl, alt, ...avatarProps }: DoctorAvatarProps) {
  const { objectUrl } = useAuthenticatedImage(photoUrl);

  return (
    <Avatar
      {...avatarProps}
      alt={alt || name || "Doctor"}
      src={objectUrl || undefined}
      imgProps={{ referrerPolicy: "no-referrer" }}
    >
      {initials(name)}
    </Avatar>
  );
}

