CREATE DATABASE IF NOT EXISTS agent_dev_assistant
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'agent_app'@'localhost'
    IDENTIFIED BY '请替换为你自己的密码';

GRANT SELECT, INSERT, UPDATE, CREATE
    ON agent_dev_assistant.*
    TO 'agent_app'@'localhost';

FLUSH PRIVILEGES;
