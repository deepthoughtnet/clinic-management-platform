import { Card, CardContent, Stack, Typography } from "@mui/material";

export default function PlaceholderPage({ title, description }: { title: string; description: string }) {
  return (
    <Card>
      <CardContent>
        <Stack spacing={1.5}>
          <Typography variant="h5" sx={{ fontWeight: 900 }}>
            {title}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {description}
          </Typography>
        </Stack>
      </CardContent>
    </Card>
  );
}
