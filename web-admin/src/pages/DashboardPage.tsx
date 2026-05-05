import { Card, CardContent, Grid, Stack, Typography } from "@mui/material";

const cards = [
  { title: "Today", text: "Appointments, tasks, and follow-up items in one view." },
  { title: "Clinical draft", text: "AI outputs stay marked as draft until a doctor reviews them." },
  { title: "Operations", text: "Tenant-scoped workflows with clinic-level governance." },
];

export default function DashboardPage() {
  return (
    <Stack spacing={3}>
      <div>
        <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>
          Dashboard
        </Typography>
        <Typography variant="body2" color="text.secondary">
          A single operational entry point for the clinic tenant.
        </Typography>
      </div>

      <Grid container spacing={2}>
        {cards.map((card) => (
          <Grid item xs={12} md={4} key={card.title}>
            <Card sx={{ height: "100%" }}>
              <CardContent>
                <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>
                  {card.title}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {card.text}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Stack>
  );
}
