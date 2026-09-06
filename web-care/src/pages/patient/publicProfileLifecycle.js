export const PUBLIC_PROFILE_STATUS = {
  LOADING: "loading",
  SUCCESS: "success",
  NOT_FOUND: "not_found",
  ERROR: "error",
};

export function createPublicProfileLifecycle({
  fetchDetail,
  onState,
  isNotFoundError = () => false,
  loadingErrorMessage,
  notFoundErrorMessage,
}) {
  let activePath = null;
  let cleanup = null;

  function setLoadingState(initialValue) {
    onState({
      data: initialValue,
      status: PUBLIC_PROFILE_STATUS.LOADING,
      errorMessage: null,
    });
  }

  function setSuccessState(result) {
    onState({
      data: result,
      status: PUBLIC_PROFILE_STATUS.SUCCESS,
      errorMessage: null,
    });
  }

  function setNotFoundState(initialValue) {
    onState({
      data: initialValue,
      status: PUBLIC_PROFILE_STATUS.NOT_FOUND,
      errorMessage: notFoundErrorMessage,
    });
  }

  function setErrorState(initialValue) {
    onState({
      data: initialValue,
      status: PUBLIC_PROFILE_STATUS.ERROR,
      errorMessage: loadingErrorMessage,
    });
  }

  return {
    update(path, initialValue) {
      if (activePath === path) {
        return cleanup ?? (() => {});
      }

      if (cleanup) {
        cleanup();
      }

      activePath = path;
      const abortController = new AbortController();
      setLoadingState(initialValue);

      let request;
      try {
        request = fetchDetail(path, abortController.signal);
      } catch {
        setErrorState(initialValue);
        cleanup = () => {
          abortController.abort();
        };
        return cleanup;
      }

      request
        .then((result) => {
          if (abortController.signal.aborted) {
            return;
          }
          setSuccessState(result);
        })
        .catch((error) => {
          if (abortController.signal.aborted) {
            return;
          }
          if (isNotFoundError(error)) {
            setNotFoundState(initialValue);
            return;
          }
          setErrorState(initialValue);
        });

      cleanup = () => {
        abortController.abort();
      };
      return cleanup;
    },
    dispose() {
      if (cleanup) {
        cleanup();
      }
      cleanup = null;
      activePath = null;
    },
  };
}
