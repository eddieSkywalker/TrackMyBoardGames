-- An optional edition or variant label, for example "2020 edition", so one game can be owned in several versions.
-- Games with no edition store an empty string, which keeps "same name, no edition" easy to compare.
ALTER TABLE board_games ADD COLUMN edition VARCHAR(100) NOT NULL DEFAULT '';