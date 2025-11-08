// service gestione associazioni utente-film
// gestisce watched/watchlist, rating personali, deduplicazione

import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import {
  UserMovieEntity,
  MovieStatus,
} from '../../database/entities/user-movie.entity';
import { MovieEntity } from '../../database/entities/movie.entity';
import { Movie } from '../../common/interfaces/movie.interface';

// interfaccia statistiche utente
export interface UserMovieStats {
  userId: string;
  totalMovies: number;
  watchedCount: number;
  watchlistCount: number;
  averageRating: number;
  watched: number;
  watchlist: number;
  total: number;
}

// interfaccia contatori import
export interface ImportCounters {
  watchedFromFile: number;
  watchlistFromFile: number;
  totalWatched: number;
  totalWatchlist: number;
}

@Injectable()
export class UserMoviesService {
  private readonly logger = new Logger(UserMoviesService.name);

  constructor(
    // repository per tabella user_movies (relazione many-to-many)
    @InjectRepository(UserMovieEntity)
    private readonly userMovieRepository: Repository<UserMovieEntity>,
    
    // repository per tabella movies (per verificare esistenza film)
    @InjectRepository(MovieEntity)
    private readonly movieRepository: Repository<MovieEntity>,
  ) {}

  /**
   * associa batch di film a utente con status specifico
   * gestisce deduplicazione intelligente:
   * - se film gia esiste come watched, non sovrascrive con watchlist
   * - se film gia esiste come watchlist, aggiorna a watched se richiesto
   * - evita duplicati nella stessa categoria
   */
  async associateMoviesToUser(
    userId: string,
    movies: Movie[],
    status: 'watched' | 'watchlist',
  ): Promise<void> {
    try {
      this.logger.log(`associazione ${movies.length} film (${status}) a user ${userId}`);

      // converti status stringa a enum moviestatus
      const movieStatus = status === 'watched' 
        ? MovieStatus.WATCHED 
        : MovieStatus.WATCHLIST;

      let created = 0;
      let updated = 0;
      let skipped = 0;

      // processa ogni film uno per uno per gestire deduplicazione
      for (const movie of movies) {
        try {
          // verifica che film esista nel database
          const movieEntity = await this.movieRepository.findOne({
            where: { id: movie.id },
          });

          if (!movieEntity) {
            this.logger.warn(`film ${movie.title} non trovato nel database, skip`);
            skipped++;
            continue;
          }

          // cerca associazione esistente per questo utente e film
          const existingUserMovie = await this.userMovieRepository.findOne({
            where: {
              userId,
              movieId: movie.id,
            },
          });

          if (existingUserMovie) {
            // associazione esiste gia
            
            // priorita watched: se film gia watched, non cambiare a watchlist
            if (
              existingUserMovie.status === MovieStatus.WATCHED &&
              movieStatus === MovieStatus.WATCHLIST
            ) {
              this.logger.debug(`film ${movie.title} gia watched, skip watchlist`);
              skipped++;
              continue;
            }

            // aggiorna status se diverso (es: da watchlist a watched)
            if (existingUserMovie.status !== movieStatus) {
              existingUserMovie.status = movieStatus;
              
              // aggiorna rating e data se forniti
              if (movie.user_rating) {
                existingUserMovie.userRating = movie.user_rating;
              }
              if (movie.watched_date) {
                existingUserMovie.watchedDate = new Date(movie.watched_date);
              }

              await this.userMovieRepository.save(existingUserMovie);
              updated++;

              if (updated <= 5) {
                this.logger.debug(`aggiornato: ${movie.title} (${movieStatus})`);
              }
            } else {
              // stesso status, skip
              skipped++;
            }
          } else {
            // crea nuova associazione
            const userMovie = this.userMovieRepository.create({
              userId,
              movieId: movie.id,
              status: movieStatus,
              userRating: movie.user_rating || null,
              watchedDate: movie.watched_date 
                ? new Date(movie.watched_date) 
                : null,
            });

            await this.userMovieRepository.save(userMovie);
            created++;

            if (created <= 5) {
              this.logger.debug(`creato: ${movie.title} (${movieStatus})`);
            }
          }
        } catch (error) {
          this.logger.error(`errore associazione film ${movie.title}:`, error);
          skipped++;
        }
      }

      // log riepilogo operazioni
      this.logger.log(`associazione completata:`);
      this.logger.log(`  creati: ${created}`);
      this.logger.log(`  aggiornati: ${updated}`);
      this.logger.log(`  skippati: ${skipped}`);

    } catch (error) {
      this.logger.error('errore associazione batch movies:', error);
      throw error;
    }
  }

  /**
   * ottieni statistiche film utente
   * conteggi watched, watchlist, rating medio
   */
  async getUserMovieStats(userId: string): Promise<UserMovieStats> {
    try {
      // recupera tutte le associazioni utente
      const allMovies = await this.userMovieRepository.find({
        where: { userId },
      });

      // conta per status
      const watched = allMovies.filter(m => m.status === MovieStatus.WATCHED).length;
      const watchlist = allMovies.filter(m => m.status === MovieStatus.WATCHLIST).length;

      // calcola rating medio considerando solo film con rating
      const ratingsSum = allMovies
        .filter(m => m.userRating !== null && m.userRating !== undefined)
        .reduce((sum, m) => sum + (m.userRating || 0), 0);
      
      const ratingsCount = allMovies.filter(m => m.userRating).length;
      const averageRating = ratingsCount > 0 ? ratingsSum / ratingsCount : 0;

      return {
        userId,
        totalMovies: allMovies.length,
        watchedCount: watched,
        watchlistCount: watchlist,
        averageRating: Math.round(averageRating * 10) / 10, // arrotonda a 1 decimale
        watched,
        watchlist,
        total: allMovies.length,
      };
    } catch (error) {
      this.logger.error(`errore recupero stats user ${userId}:`, error);
      throw error;
    }
  }

  /**
   * recupera tutti i film di un utente
   * opzionalmente filtra per status (watched o watchlist)
   */
  async getUserMovies(
    userId: string,
    status?: 'watched' | 'watchlist',
  ): Promise<Movie[]> {
    try {
      // costruisci query con join su tabella movies
      const queryBuilder = this.userMovieRepository
        .createQueryBuilder('userMovie')
        .leftJoinAndSelect('userMovie.movie', 'movie')
        .where('userMovie.userId = :userId', { userId });

      // filtra per status se fornito
      if (status) {
        const movieStatus = status === 'watched' 
          ? MovieStatus.WATCHED 
          : MovieStatus.WATCHLIST;
        queryBuilder.andWhere('userMovie.status = :status', { status: movieStatus });
      }

      const userMovies = await queryBuilder.getMany();

      // mappa a oggetti movie includendo dati utente
      return userMovies.map(um => ({
        ...um.movie, // dati film dal database
        user_rating: um.userRating, // rating personale
        watched_date: um.watchedDate?.toISOString(), // data visione
        status: um.status === MovieStatus.WATCHED ? 'watched' : 'watchlist',
      }));
    } catch (error) {
      this.logger.error(`errore recupero movies user ${userId}:`, error);
      throw error;
    }
  }
}