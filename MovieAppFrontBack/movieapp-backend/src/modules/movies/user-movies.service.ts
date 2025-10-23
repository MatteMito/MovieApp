// servizio gestione relazioni utente-film (user_movies)

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { UserMovieEntity, MovieStatus } from '../../database/entities/user-movie.entity';
import { MovieEntity } from '../../database/entities/movie.entity';

export interface UserMovieStats {
  totalMovies: number;
  watchedCount: number;
  watchlistCount: number;
  averageRating?: number;
  lastImportDate?: Date;
}

export interface ImportCounters {
  watchedFromFile: number;  // Dal file appena importato
  watchlistFromFile: number; // Dal file appena importato
  totalWatched: number;      // Totale assoluto dopo refresh
  totalWatchlist: number;    // Totale assoluto dopo refresh
}

@Injectable()
export class UserMoviesService {
  private readonly logger = new Logger(UserMoviesService.name);

  constructor(
    @InjectRepository(UserMovieEntity)
    private userMovieRepository: Repository<UserMovieEntity>,
    @InjectRepository(MovieEntity)
    private movieRepository: Repository<MovieEntity>,
  ) {}

  /**
   * Associa un film ad un utente con status specifico
   * Se l'associazione esiste già, la aggiorna
   */
  async associateMovieToUser(
    userId: string,
    movieId: string,
    status: MovieStatus,
    source: string,
    userRating?: number,
    watchedDate?: Date,
    userReview?: string,
  ): Promise<UserMovieEntity> {
    try {
      // Verifica se l'associazione esiste già
      let userMovie = await this.userMovieRepository.findOne({
        where: { userId, movieId },
      });

      if (userMovie) {
        // Aggiorna l'associazione esistente
        userMovie.status = status;
        userMovie.source = source;
        userMovie.userRating = userRating;
        userMovie.watchedDate = watchedDate;
        userMovie.userReview = userReview;
        
        this.logger.debug(`🔄 Aggiornamento associazione: user ${userId} - movie ${movieId}`);
      } else {
        // Crea nuova associazione
        userMovie = this.userMovieRepository.create({
          userId,
          movieId,
          status,
          source,
          userRating,
          watchedDate,
          userReview,
        });
        
        this.logger.debug(`➕ Nuova associazione: user ${userId} - movie ${movieId} [${status}]`);
      }

      return await this.userMovieRepository.save(userMovie);
    } catch (error) {
      this.logger.error(`Errore associazione film-utente: ${error.message}`);
      throw error;
    }
  }

  /**
   * Associa multipli film ad un utente in batch
   * Ritorna i contatori per file importato
   */
  async batchAssociateMoviesToUser(
    userId: string,
    movies: Array<{
      movieId: string;
      status: MovieStatus;
      source: string;
      userRating?: number;
      watchedDate?: Date;
      userReview?: string;
    }>,
  ): Promise<{ created: number; updated: number; watchedInFile: number; watchlistInFile: number }> {
    try {
      this.logger.log(`📦 Batch associazione: ${movies.length} film per utente ${userId}`);

      let created = 0;
      let updated = 0;
      let watchedInFile = 0;
      let watchlistInFile = 0;

      // Conta i film nel file per tipo
      watchedInFile = movies.filter(m => m.status === MovieStatus.WATCHED).length;
      watchlistInFile = movies.filter(m => m.status === MovieStatus.WATCHLIST).length;

      // Processa in chunk per performance
      const CHUNK_SIZE = 100;
      for (let i = 0; i < movies.length; i += CHUNK_SIZE) {
        const chunk = movies.slice(i, i + CHUNK_SIZE);

        for (const movie of chunk) {
          const existing = await this.userMovieRepository.findOne({
            where: { userId, movieId: movie.movieId },
          });

          if (existing) {
            // Aggiorna esistente
            existing.status = movie.status;
            existing.source = movie.source;
            existing.userRating = movie.userRating;
            existing.watchedDate = movie.watchedDate;
            existing.userReview = movie.userReview;
            await this.userMovieRepository.save(existing);
            updated++;
          } else {
            // Crea nuovo
            const userMovie = this.userMovieRepository.create({
              userId,
              movieId: movie.movieId,
              status: movie.status,
              source: movie.source,
              userRating: movie.userRating,
              watchedDate: movie.watchedDate,
              userReview: movie.userReview,
            });
            await this.userMovieRepository.save(userMovie);
            created++;
          }
        }

        this.logger.log(`✅ Chunk ${Math.floor(i / CHUNK_SIZE) + 1}: processati ${chunk.length} film`);
      }

      this.logger.log(`=== BATCH COMPLETATO ===`);
      this.logger.log(`Creati: ${created}`);
      this.logger.log(`Aggiornati: ${updated}`);
      this.logger.log(`Watched nel file: ${watchedInFile}`);
      this.logger.log(`Watchlist nel file: ${watchlistInFile}`);

      return { created, updated, watchedInFile, watchlistInFile };
    } catch (error) {
      this.logger.error(`Errore batch associazione: ${error.message}`);
      throw error;
    }
  }

  /**
   * Recupera tutti i film di un utente
   */
  async getUserMovies(
    userId: string,
    status?: MovieStatus,
  ): Promise<Array<UserMovieEntity & { movie: MovieEntity }>> {
    try {
      const whereClause: any = { userId };
      if (status) {
        whereClause.status = status;
      }

      const userMovies = await this.userMovieRepository.find({
        where: whereClause,
        relations: ['movie'],
        order: { createdAt: 'DESC' },
      });

      this.logger.debug(`📚 Recuperati ${userMovies.length} film per utente ${userId}`);

      return userMovies as Array<UserMovieEntity & { movie: MovieEntity }>;
    } catch (error) {
      this.logger.error(`Errore recupero film utente: ${error.message}`);
      return [];
    }
  }

  /**
   * Recupera le statistiche dei film di un utente
   * IMPORTANTE: Questi sono i contatori TOTALI dopo refresh
   */
  async getUserMovieStats(userId: string): Promise<UserMovieStats> {
    try {
      const [totalMovies, watchedCount, watchlistCount] = await Promise.all([
        this.userMovieRepository.count({ where: { userId } }),
        this.userMovieRepository.count({ where: { userId, status: MovieStatus.WATCHED } }),
        this.userMovieRepository.count({ where: { userId, status: MovieStatus.WATCHLIST } }),
      ]);

      // Calcola media rating
      const userMoviesWithRating = await this.userMovieRepository.find({
        where: { userId },
        select: ['userRating'],
      });

      const ratings = userMoviesWithRating
        .filter((um) => um.userRating !== null && um.userRating !== undefined)
        .map((um) => um.userRating);

      const averageRating =
        ratings.length > 0
          ? ratings.reduce((sum, rating) => sum + rating, 0) / ratings.length
          : undefined;

      // Ultima data di import
      const lastImport = await this.userMovieRepository.findOne({
        where: { userId },
        order: { createdAt: 'DESC' },
        select: ['createdAt'],
      });

      const stats: UserMovieStats = {
        totalMovies,
        watchedCount,
        watchlistCount,
        averageRating,
        lastImportDate: lastImport?.createdAt,
      };

      this.logger.debug(`📊 Statistiche utente ${userId}:`, stats);

      return stats;
    } catch (error) {
      this.logger.error(`Errore statistiche utente: ${error.message}`);
      return {
        totalMovies: 0,
        watchedCount: 0,
        watchlistCount: 0,
      };
    }
  }

  /**
   * Rimuove associazione film-utente
   */
  async removeMovieFromUser(userId: string, movieId: string): Promise<void> {
    try {
      const result = await this.userMovieRepository.delete({ userId, movieId });

      if (result.affected > 0) {
        this.logger.log(`🗑️ Rimossa associazione: user ${userId} - movie ${movieId}`);
      } else {
        this.logger.warn(`⚠️ Associazione non trovata: user ${userId} - movie ${movieId}`);
      }
    } catch (error) {
      this.logger.error(`Errore rimozione associazione: ${error.message}`);
      throw error;
    }
  }

  /**
   * Rimuove tutti i film di un utente
   */
  async removeAllUserMovies(userId: string): Promise<number> {
    try {
      const result = await this.userMovieRepository.delete({ userId });
      const deletedCount = result.affected || 0;

      this.logger.log(`🗑️ Rimossi ${deletedCount} film per utente ${userId}`);

      return deletedCount;
    } catch (error) {
      this.logger.error(`Errore rimozione film utente: ${error.message}`);
      throw error;
    }
  }

  /**
   * Verifica se un utente ha già un film
   */
  async userHasMovie(userId: string, movieId: string): Promise<boolean> {
    try {
      const count = await this.userMovieRepository.count({
        where: { userId, movieId },
      });

      return count > 0;
    } catch (error) {
      this.logger.error(`Errore verifica film utente: ${error.message}`);
      return false;
    }
  }

  /**
   * Recupera i movie_id dei film di un utente (utile per query veloci)
   */
  async getUserMovieIds(userId: string, status?: MovieStatus): Promise<string[]> {
    try {
      const whereClause: any = { userId };
      if (status) {
        whereClause.status = status;
      }

      const userMovies = await this.userMovieRepository.find({
        where: whereClause,
        select: ['movieId'],
      });

      return userMovies.map((um) => um.movieId);
    } catch (error) {
      this.logger.error(`Errore recupero movie IDs: ${error.message}`);
      return [];
    }
  }
}