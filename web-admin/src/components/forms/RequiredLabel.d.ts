import type * as React from "react";

declare type RequiredLabelProps = {
  text: React.ReactNode;
  required?: boolean;
};

export default function RequiredLabel(props: RequiredLabelProps): React.JSX.Element;
