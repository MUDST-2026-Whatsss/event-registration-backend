-- One refresh token can have at most one successor. The application also serializes rotation with
-- SELECT ... FOR UPDATE; this constraint is the final database-level invariant if that code ever
-- regresses or another writer is introduced.
CREATE UNIQUE INDEX ux_auth_sessions_parent_session_id
    ON auth_sessions (parent_session_id)
    WHERE parent_session_id IS NOT NULL;

COMMENT ON INDEX ux_auth_sessions_parent_session_id IS
    'Enforces one-time refresh rotation: a session can issue at most one direct successor.';
