import { ApolloClient, InMemoryCache, createHttpLink } from "@apollo/client";
import { setContext } from "@apollo/client/link/context";
import { keycloak } from "../auth/keycloak";

const httpLink = createHttpLink({
  uri: "/graphql",
});

const authLink = setContext((_, { headers }) => ({
  headers: {
    ...headers,
    ...(keycloak.token ? { Authorization: `Bearer ${keycloak.token}` } : {}),
  },
}));

export const apolloClient = new ApolloClient({
  link: authLink.concat(httpLink),
  cache: new InMemoryCache(),
});
