--liquibase formatted sql

-- This file is used to test filters.  The id needs to match the constants in
-- DatabaseChangeLogDelegateIncludeAllTests.groovy
--changeset ssaliman:fourth-included-change-set
alter table monkey add gender varchar(1);
