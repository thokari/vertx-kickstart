CREATE TABLE registration (
    email VARCHAR(256) NOT NULL PRIMARY KEY,
    email_confirmed BOOLEAN,
    permissions VARCHAR(64),
    password VARCHAR(256),
    created DATE
);
