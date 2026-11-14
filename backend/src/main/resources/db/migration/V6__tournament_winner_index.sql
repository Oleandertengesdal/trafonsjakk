-- Tournament wins are counted on every profile page (TournamentRepository.countWins).
CREATE INDEX ix_tournaments_winner ON tournaments (winner_id);
