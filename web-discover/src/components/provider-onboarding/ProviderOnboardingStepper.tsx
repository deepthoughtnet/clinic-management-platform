export type ProviderOnboardingStepState = {
  id: string;
  label: string;
  completed: boolean;
  current: boolean;
  disabled: boolean;
  errorCount: number;
  summary?: string;
};

export function ProviderOnboardingStepper({
  steps,
  onSelect,
}: {
  steps: ProviderOnboardingStepState[];
  onSelect: (stepId: string) => void;
}) {
  return (
    <aside className="provider-stepper" aria-label="Provider onboarding steps">
      {steps.map((step, index) => (
        <button
          key={step.id}
          className={[
            step.current ? "is-current" : "",
            step.completed ? "is-complete" : "",
          ].filter(Boolean).join(" ")}
          type="button"
          onClick={() => onSelect(step.id)}
          disabled={step.disabled}
          aria-current={step.current ? "step" : undefined}
        >
          <span aria-hidden="true">{step.completed ? "✓" : index + 1}</span>
          <strong>{step.label}</strong>
          {step.summary ? <small>{step.summary}</small> : null}
          {step.errorCount ? <small className="provider-step-error">{step.errorCount} issue{step.errorCount === 1 ? "" : "s"}</small> : null}
        </button>
      ))}
    </aside>
  );
}

export function ProviderSaveStatus({
  saving,
  statusMessage,
  autosaveEnabled = true,
  unsavedChanges,
  conflict,
}: {
  saving: boolean;
  statusMessage: string;
  autosaveEnabled?: boolean;
  unsavedChanges: boolean;
  conflict: boolean;
}) {
  const stateLabel = conflict ? "Conflict" : saving ? "Saving..." : unsavedChanges ? "Unsaved changes" : "No unsaved changes";
  return (
    <div className={`autosave-row provider-save-status ${saving ? "is-saving" : ""} ${conflict ? "is-conflict" : ""}`} role="status" aria-live="polite">
      <div className="provider-save-copy">
        <strong>{stateLabel}</strong>
        <span>{statusMessage}</span>
      </div>
      <div className="provider-save-meta">
        <span className={`provider-save-pill ${saving ? "is-saving" : unsavedChanges ? "is-dirty" : "is-clean"}`}>{saving ? "Saving" : unsavedChanges ? "Pending" : "Saved"}</span>
        {autosaveEnabled ? <span className="provider-save-hint">Autosave enabled</span> : null}
      </div>
    </div>
  );
}
