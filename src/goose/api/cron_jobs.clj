(ns goose.api.cron-jobs
  "API to manage cron-jobs AKA periodic jobs.
  To update a cron entry, call goose.client/perform-every
  with the cron entry name."
  (:require
    [goose.brokers.broker :as b]))

(defn find-by-name
  "Look up a cron entry by name."
  [broker entry-name]
  (b/cron-jobs-find-by-name broker entry-name))

(defn delete
  "Delete a cron entry."
  [broker entry-name]
  (b/cron-jobs-delete broker entry-name))

(defn delete-all
  "Delete all cron entries."
  [broker]
  (b/cron-jobs-delete-all broker))