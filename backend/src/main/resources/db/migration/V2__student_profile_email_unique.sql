-- V2__student_email_unique.sql — enforce a unique student email address.
-- Self-service registration (POST /api/auth/register) makes email uniqueness
-- externally reachable, so the constraint must exist at the database level and
-- not rely solely on the application-level lookup.
ALTER TABLE student_profiles
    ADD CONSTRAINT uk_student_profiles_email UNIQUE (email);
