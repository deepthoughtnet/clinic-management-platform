export declare const PUBLIC_PROFILE_STATUS: {
  readonly LOADING: "loading";
  readonly SUCCESS: "success";
  readonly NOT_FOUND: "not_found";
  readonly ERROR: "error";
};

export type PublicProfileStatus = typeof PUBLIC_PROFILE_STATUS[keyof typeof PUBLIC_PROFILE_STATUS];

export type PublicProfileState<T> = {
  data: T;
  status: PublicProfileStatus;
  errorMessage: string | null;
};

export type PublicProfileLifecycleOptions<T> = {
  fetchDetail: (path: string, signal: AbortSignal) => Promise<T>;
  onState: (state: PublicProfileState<T>) => void;
  isNotFoundError?: (error: unknown) => boolean;
  loadingErrorMessage: string;
  notFoundErrorMessage: string;
};

export type PublicProfileLifecycle<T> = {
  update: (path: string, initialValue: T) => () => void;
  dispose: () => void;
};

export declare function createPublicProfileLifecycle<T>(
  options: PublicProfileLifecycleOptions<T>,
): PublicProfileLifecycle<T>;
