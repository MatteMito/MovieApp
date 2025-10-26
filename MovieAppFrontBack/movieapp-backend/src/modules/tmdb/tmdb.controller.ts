// FILE: movieapp-backend/src/modules/tmdb/tmdb.controller.ts
// Controller per ricerca film TMDB

import {
  Controller,
  Get,
  Post,
  Query,
  Body,
  HttpStatus,
  HttpException,
  Logger,
} from '@nestjs/common';
import { TmdbService } from './tmdb.service';
import { DatabaseService } from '../../database/database.service';
import { Movie } from '../../common/interfaces/movie.interface';

interface ApiResponse<T = any> {
  success: boolean;
  data?: T;
  message?: string;
  timestamp: string;
}

@Controller('api/v1/tmdb')
export class TmdbController {
  private readonly logger = new Logger(TmdbController.name);

  constructor(
    private readonly tmdbService: TmdbService,
    private readonly databaseService: DatabaseService,
  ) {}

  /**
   * GET /api/v1/tmdb/search?query=inception&year=2010
   * Cerca film su TMDB
   */
  @Get('search')
  async searchMovie(
    @Query('query') query: string,
    @Query('year') year?: string,
  ): Promise<ApiResponse> {
    try {
      if (!query) {
        throw new HttpException(
          { success: false, message: 'Query mancante', timestamp: new Date().toISOString() },
          HttpStatus.BAD_REQUEST,
        );
      }

      this.logger.log(`🔍 Ricerca TMDB: ${query}${year ? ` (${year})` : ''}`);

      const yearNum = year ? parseInt(year, 10) : undefined;
      
      // Cerca su TMDB
      const tmdbMovie = await this.tmdbService.searchByTitle(query, yearNum);

      if (!tmdbMovie) {
        return {
          success: false,
          message: 'Nessun film trovato su TMDB',
          timestamp: new Date().toISOString(),
        };
      }

      // Converti in formato Movie
      const movie: Partial<Movie> = {
        title: tmdbMovie.title,
        year: tmdbMovie.release_date ? parseInt(tmdbMovie.release_date.substring(0, 4)) : undefined,
        tmdb_id: tmdbMovie.id,
        genres: tmdbMovie.genres?.map((g) => g.name) || [],
        director: tmdbMovie.credits?.crew?.find((c) => c.job === 'Director')?.name,
        actors: tmdbMovie.credits?.cast?.slice(0, 5).map((a) => a.name) || [],
        overview: tmdbMovie.overview,
        tagline: tmdbMovie.tagline,
        poster_url: tmdbMovie.poster_path
          ? `https://image.tmdb.org/t/p/w500${tmdbMovie.poster_path}`
          : undefined,
        backdrop_url: tmdbMovie.backdrop_path
          ? `https://image.tmdb.org/t/p/original${tmdbMovie.backdrop_path}`
          : undefined,
        tmdb_rating: tmdbMovie.vote_average,
        vote_count: tmdbMovie.vote_count,
        runtime: tmdbMovie.runtime,
        imdb_id: tmdbMovie.imdb_id,
      };

      this.logger.log(`✅ Film trovato: ${movie.title} (TMDB ID: ${movie.tmdb_id})`);

      return {
        success: true,
        data: movie,
        message: 'Film trovato su TMDB',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`❌ Errore ricerca TMDB: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * POST /api/v1/tmdb/add-to-database
   * Aggiunge un film da TMDB al database e associa all'utente
   */
  @Post('add-to-database')
  async addMovieFromTmdb(
    @Body() body: { tmdb_id: number; user_id: string; status?: string },
  ): Promise<ApiResponse> {
    try {
      const { tmdb_id, user_id, status = 'watchlist' } = body;

      if (!tmdb_id || !user_id) {
        throw new HttpException(
          { success: false, message: 'tmdb_id e user_id richiesti', timestamp: new Date().toISOString() },
          HttpStatus.BAD_REQUEST,
        );
      }

      this.logger.log(`📥 Aggiunta film da TMDB: ${tmdb_id} per utente ${user_id}`);

      // 1. Cerca dettagli completi su TMDB
      const tmdbMovie = await this.tmdbService['getMovieDetails'](tmdb_id);

      if (!tmdbMovie) {
        throw new Error('Film non trovato su TMDB');
      }

      // 2. Crea oggetto Movie
      const movie: Movie = {
        id: `tmdb_${tmdb_id}`,
        title: tmdbMovie.title,
        year: tmdbMovie.release_date ? parseInt(tmdbMovie.release_date.substring(0, 4)) : 0,
        source: 'tmdb',
        tmdb_id: tmdbMovie.id,
        genres: tmdbMovie.genres?.map((g) => g.name) || [],
        director: tmdbMovie.credits?.crew?.find((c) => c.job === 'Director')?.name,
        actors: tmdbMovie.credits?.cast?.slice(0, 5).map((a) => a.name) || [],
        overview: tmdbMovie.overview,
        tagline: tmdbMovie.tagline,
        poster_url: tmdbMovie.poster_path
          ? `https://image.tmdb.org/t/p/w500${tmdbMovie.poster_path}`
          : undefined,
        backdrop_url: tmdbMovie.backdrop_path
          ? `https://image.tmdb.org/t/p/original${tmdbMovie.backdrop_path}`
          : undefined,
        tmdb_rating: tmdbMovie.vote_average,
        vote_count: tmdbMovie.vote_count,
        runtime: tmdbMovie.runtime,
        budget: tmdbMovie.budget,
        revenue: tmdbMovie.revenue,
        status: tmdbMovie.status,
        original_language: tmdbMovie.original_language,
        original_title: tmdbMovie.original_title,
        popularity: tmdbMovie.popularity,
        adult: tmdbMovie.adult,
        homepage: tmdbMovie.homepage,
        imdb_id: tmdbMovie.imdb_id,
        production_companies: tmdbMovie.production_companies?.map((c) => c.name) || [],
        production_countries: tmdbMovie.production_countries?.map((c) => c.name) || [],
        spoken_languages: tmdbMovie.spoken_languages?.map((l) => l.name) || [],
        keywords: tmdbMovie.keywords?.keywords?.slice(0, 10).map((k) => k.name) || [],
        certification: tmdbMovie.releases?.countries?.find((c) => c.iso_3166_1 === 'US')?.certification,
        trailer_url: tmdbMovie.videos?.results?.find((v) => v.type === 'Trailer')?.key
          ? `https://www.youtube.com/watch?v=${tmdbMovie.videos.results.find((v) => v.type === 'Trailer')?.key}`
          : undefined,
      };

      // 3. Salva nel database
      const savedMovie = await this.databaseService.saveMovie(movie);

      this.logger.log(`✅ Film salvato nel database: ${savedMovie.id}`);

      return {
        success: true,
        data: savedMovie,
        message: 'Film aggiunto al database',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`❌ Errore aggiunta film: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }
}