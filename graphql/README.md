# GraphQL

- [ ] Provide an SDL schema.
- [ ] Implement at least two queries.
- [ ] Implement the following authentication mutations.

```graphql
type Mutation {
  login(username: String!, password: String!): String!
  logout: Boolean!
}
```

- [ ] Use bearer-token authentication and harden the token via hashing.
- [ ] Implement at least two additional mutations available only to authenticated users.
- [ ] Provide a test suite in Postman or a similar tool with positive and negative cases.

See the [shared exam requirements](../README.md#shared-requirements).
