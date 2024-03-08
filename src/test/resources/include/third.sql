--liquibase formatted sql

-- This file should excluded because it is not a groovy file

--changeset author:ssaliman id:included-change-set-3
alter table monkey add gender varchar(1);
