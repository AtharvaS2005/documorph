--
-- PostgreSQL database dump
--

\restrict UmegTm3rUDWZcfEGMZWS3P5hTBzYHXD8TYLDeZZx02xYIiIJ8MTOfO7N05AP4RR

-- Dumped from database version 18.6 (Debian 18.6-1.pgdg13+2)
-- Dumped by pg_dump version 18.6 (Debian 18.6-1.pgdg13+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: uuid-ossp; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;


--
-- Name: category_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.category_type_enum AS ENUM (
    'DOCUMENT',
    'RECORD'
);


--
-- Name: extraction_mode_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.extraction_mode_enum AS ENUM (
    'FULL',
    'SUMMARY',
    'KEY_INSIGHTS',
    'CUSTOM_FIELDS'
);


--
-- Name: job_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.job_status_enum AS ENUM (
    'QUEUED',
    'PROCESSING',
    'COMPLETED',
    'FAILED'
);


--
-- Name: session_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.session_status_enum AS ENUM (
    'QUEUED',
    'PROCESSING',
    'COMPLETED',
    'PARTIAL_FAILURE',
    'FAILED'
);


--
-- Name: session_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.session_type_enum AS ENUM (
    'CATEGORY_RUN',
    'SINGLE_SESSION'
);


--
-- Name: storage_format_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.storage_format_enum AS ENUM (
    'DOCUMENT_WISE',
    'CONSOLIDATED'
);


--
-- Name: summary_style_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.summary_style_enum AS ENUM (
    'SHORT',
    'BRIEF'
);


--
-- Name: user_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.user_type_enum AS ENUM (
    'SOLO',
    'ORGANIZATION'
);


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categories (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    workspace_id uuid NOT NULL,
    category_name character varying(255) NOT NULL,
    category_type character varying NOT NULL,
    default_extraction_mode character varying NOT NULL,
    summary_style character varying,
    custom_prompt text,
    storage_format character varying,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    description text,
    CONSTRAINT check_record_category_storage CHECK ((((category_type)::text <> 'RECORD'::text) OR (storage_format IS NOT NULL)))
);


--
-- Name: chat_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chat_logs (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    user_id uuid,
    workspace_id uuid,
    question text NOT NULL,
    answer text,
    outcome character varying(30) NOT NULL,
    sources character varying(255),
    passages_used integer DEFAULT 0 NOT NULL,
    sanitized_passages integer DEFAULT 0 CONSTRAINT chat_logs_withheld_passages_not_null NOT NULL,
    redactions integer DEFAULT 0 NOT NULL,
    latency_ms integer,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: event_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_logs (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    actor_user_id uuid,
    workspace_id uuid,
    event_type character varying(100) NOT NULL,
    target_id character varying(255),
    event_timestamp timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: extraction_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.extraction_jobs (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    file_id uuid NOT NULL,
    extraction_mode public.extraction_mode_enum NOT NULL,
    summary_style public.summary_style_enum,
    custom_prompt text,
    status character varying DEFAULT 'QUEUED'::character varying,
    error_code character varying(100),
    failure_reason text,
    retry_count integer DEFAULT 0,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    external_job_id character varying,
    created_at timestamp with time zone DEFAULT now(),
    progress double precision
);


--
-- Name: extraction_outputs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.extraction_outputs (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    job_id uuid NOT NULL,
    extracted_text text,
    structured_json jsonb,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    tables jsonb,
    field_locations jsonb
);


--
-- Name: file_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.file_items (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    session_id uuid NOT NULL,
    file_name character varying(255) NOT NULL,
    file_size bigint NOT NULL,
    mime_type character varying(100),
    uploaded_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    file_path character varying NOT NULL
);


--
-- Name: forum_posts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forum_posts (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    organization_id uuid NOT NULL,
    author_id uuid NOT NULL,
    session_id uuid,
    record_set_id uuid,
    title character varying(255) NOT NULL,
    message text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: forum_replies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forum_replies (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    post_id uuid NOT NULL,
    author_id uuid NOT NULL,
    message text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: organizations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organizations (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    name character varying(255) NOT NULL,
    invite_code character varying(100),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: record_sets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.record_sets (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    category_id uuid NOT NULL,
    workspace_id uuid NOT NULL,
    current_version integer DEFAULT 1,
    storage_format public.storage_format_enum NOT NULL,
    merged_content jsonb,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: upload_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.upload_sessions (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    workspace_id uuid NOT NULL,
    category_id uuid,
    session_type public.session_type_enum NOT NULL,
    status public.session_status_enum DEFAULT 'QUEUED'::public.session_status_enum,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    user_type character varying(50),
    is_setup_completed boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: workspaces; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workspaces (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    user_id uuid NOT NULL,
    organization_id uuid,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);


--
-- Name: chat_logs chat_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_logs
    ADD CONSTRAINT chat_logs_pkey PRIMARY KEY (id);


--
-- Name: event_logs event_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_logs
    ADD CONSTRAINT event_logs_pkey PRIMARY KEY (id);


--
-- Name: extraction_jobs extraction_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.extraction_jobs
    ADD CONSTRAINT extraction_jobs_pkey PRIMARY KEY (id);


--
-- Name: extraction_outputs extraction_outputs_job_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.extraction_outputs
    ADD CONSTRAINT extraction_outputs_job_id_key UNIQUE (job_id);


--
-- Name: extraction_outputs extraction_outputs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.extraction_outputs
    ADD CONSTRAINT extraction_outputs_pkey PRIMARY KEY (id);


--
-- Name: file_items file_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.file_items
    ADD CONSTRAINT file_items_pkey PRIMARY KEY (id);


--
-- Name: forum_posts forum_posts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_posts
    ADD CONSTRAINT forum_posts_pkey PRIMARY KEY (id);


--
-- Name: forum_replies forum_replies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_replies
    ADD CONSTRAINT forum_replies_pkey PRIMARY KEY (id);


--
-- Name: organizations organizations_invite_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organizations
    ADD CONSTRAINT organizations_invite_code_key UNIQUE (invite_code);


--
-- Name: organizations organizations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organizations
    ADD CONSTRAINT organizations_pkey PRIMARY KEY (id);


--
-- Name: record_sets record_sets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.record_sets
    ADD CONSTRAINT record_sets_pkey PRIMARY KEY (id);


--
-- Name: upload_sessions upload_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.upload_sessions
    ADD CONSTRAINT upload_sessions_pkey PRIMARY KEY (id);


--
-- Name: record_sets uq_record_sets_category; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.record_sets
    ADD CONSTRAINT uq_record_sets_category UNIQUE (category_id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: workspaces workspaces_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workspaces
    ADD CONSTRAINT workspaces_pkey PRIMARY KEY (id);


--
-- Name: idx_categories_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_categories_workspace ON public.categories USING btree (workspace_id);


--
-- Name: idx_chat_logs_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_logs_workspace ON public.chat_logs USING btree (workspace_id, created_at DESC);


--
-- Name: idx_files_session; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_files_session ON public.file_items USING btree (session_id);


--
-- Name: idx_jobs_file; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_jobs_file ON public.extraction_jobs USING btree (file_id);


--
-- Name: idx_logs_actor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_logs_actor ON public.event_logs USING btree (actor_user_id);


--
-- Name: idx_outputs_job; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outputs_job ON public.extraction_outputs USING btree (job_id);


--
-- Name: idx_posts_org; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_posts_org ON public.forum_posts USING btree (organization_id);


--
-- Name: idx_replies_post; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_replies_post ON public.forum_replies USING btree (post_id);


--
-- Name: idx_sessions_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sessions_workspace ON public.upload_sessions USING btree (workspace_id);


--
-- Name: idx_workspaces_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workspaces_user ON public.workspaces USING btree (user_id);


--
-- Name: categories categories_workspace_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_workspace_id_fkey FOREIGN KEY (workspace_id) REFERENCES public.workspaces(id) ON DELETE CASCADE;


--
-- Name: chat_logs chat_logs_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_logs
    ADD CONSTRAINT chat_logs_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: chat_logs chat_logs_workspace_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_logs
    ADD CONSTRAINT chat_logs_workspace_id_fkey FOREIGN KEY (workspace_id) REFERENCES public.workspaces(id) ON DELETE CASCADE;


--
-- Name: event_logs event_logs_actor_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_logs
    ADD CONSTRAINT event_logs_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: event_logs event_logs_workspace_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_logs
    ADD CONSTRAINT event_logs_workspace_id_fkey FOREIGN KEY (workspace_id) REFERENCES public.workspaces(id) ON DELETE SET NULL;


--
-- Name: extraction_jobs extraction_jobs_file_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.extraction_jobs
    ADD CONSTRAINT extraction_jobs_file_id_fkey FOREIGN KEY (file_id) REFERENCES public.file_items(id) ON DELETE CASCADE;


--
-- Name: extraction_outputs extraction_outputs_job_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.extraction_outputs
    ADD CONSTRAINT extraction_outputs_job_id_fkey FOREIGN KEY (job_id) REFERENCES public.extraction_jobs(id) ON DELETE CASCADE;


--
-- Name: file_items file_items_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.file_items
    ADD CONSTRAINT file_items_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.upload_sessions(id) ON DELETE CASCADE;


--
-- Name: forum_posts forum_posts_author_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_posts
    ADD CONSTRAINT forum_posts_author_id_fkey FOREIGN KEY (author_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: forum_posts forum_posts_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_posts
    ADD CONSTRAINT forum_posts_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE CASCADE;


--
-- Name: forum_posts forum_posts_record_set_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_posts
    ADD CONSTRAINT forum_posts_record_set_id_fkey FOREIGN KEY (record_set_id) REFERENCES public.record_sets(id) ON DELETE SET NULL;


--
-- Name: forum_posts forum_posts_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_posts
    ADD CONSTRAINT forum_posts_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.upload_sessions(id) ON DELETE SET NULL;


--
-- Name: forum_replies forum_replies_author_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_replies
    ADD CONSTRAINT forum_replies_author_id_fkey FOREIGN KEY (author_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: forum_replies forum_replies_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_replies
    ADD CONSTRAINT forum_replies_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.forum_posts(id) ON DELETE CASCADE;


--
-- Name: record_sets record_sets_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.record_sets
    ADD CONSTRAINT record_sets_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories(id) ON DELETE CASCADE;


--
-- Name: record_sets record_sets_workspace_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.record_sets
    ADD CONSTRAINT record_sets_workspace_id_fkey FOREIGN KEY (workspace_id) REFERENCES public.workspaces(id) ON DELETE CASCADE;


--
-- Name: upload_sessions upload_sessions_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.upload_sessions
    ADD CONSTRAINT upload_sessions_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories(id) ON DELETE SET NULL;


--
-- Name: upload_sessions upload_sessions_workspace_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.upload_sessions
    ADD CONSTRAINT upload_sessions_workspace_id_fkey FOREIGN KEY (workspace_id) REFERENCES public.workspaces(id) ON DELETE CASCADE;


--
-- Name: workspaces workspaces_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workspaces
    ADD CONSTRAINT workspaces_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE SET NULL;


--
-- Name: workspaces workspaces_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workspaces
    ADD CONSTRAINT workspaces_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict UmegTm3rUDWZcfEGMZWS3P5hTBzYHXD8TYLDeZZx02xYIiIJ8MTOfO7N05AP4RR

