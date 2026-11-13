# Software Integrations – Exam Project

The Second Mandatory Assignment is the exam project. Work in groups of 4 or 5 and register the members in [Groups – Exam Project.xlsx](https://erhvervsakademikbenhavn.sharepoint.com/:x:/s/TeamTeamSOFT-E26-SystemIntegration/IQDXqBBpM7IfR6KntotZRPKPAZbjaED34NuixQGndo_UcbI?e=R1DHPg). If major circumstances prevent group work, request a dispensation from Head of Education Marc Kluge ([klu@ek.dk](mailto:klu@ek.dk)).

## APIs

- [REST](rest/README.md)
- [SOAP](soap/README.md)
- [GraphQL](graphql/README.md)
- [gRPC](grpc/README.md)
- [WebSockets](websockets/README.md)
- [Server-Sent Events](server-sent-events/README.md)

## Shared requirements

- [ ] Develop six independent APIs. Choose the business scenario for each API and select your own languages, frameworks, libraries, databases and testing tools.
- [ ] All APIs interact with database-stored information. The group chooses the data models and database management systems.
- [ ] All APIs prevent SQL injection, XSS and CSRF.
- [ ] All six APIs and the ETL process are original work. They must differ from existing class exercises and the First Mandatory Assignment and may not be simple extensions of either.

## ETL requirements

The group chooses one of the six APIs to implement ETL.

- [ ] Use three independent database shards with equivalent structures and at least two related entity types.
- [ ] Distribute data across the shards with a hash-based function.
- [ ] Route API reads and writes automatically to the appropriate shard.
- [ ] Extract data from all three shards.
- [ ] Transform the data to fit the destination model and resolve possible identifier conflicts.
- [ ] Load the consolidated data into a separate database.
- [ ] Verify that data distribution and API routing are correct and that the destination contains data from all three shards.
- [ ] Verify that rerunning ETL creates no duplicate records or missing information.
