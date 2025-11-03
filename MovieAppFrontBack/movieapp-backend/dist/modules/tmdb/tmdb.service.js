"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
var TmdbService_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.TmdbService = void 0;
const common_1 = require("@nestjs/common");
const axios_1 = require("@nestjs/axios");
const config_1 = require("@nestjs/config");
const rxjs_1 = require("rxjs");
const database_service_1 = require("../../database/database.service");
let TmdbService = TmdbService_1 = class TmdbService {
    constructor(httpService, configService, databaseService) {
        this.httpService = httpService;
        this.configService = configService;
        this.databaseService = databaseService;
        this.logger = new common_1.Logger(TmdbService_1.name);
        this.rateLimitWindow = 10000;
        this.maxRequestsPerWindow = 35;
        this.requestHistory = [];
        this.baseUrl = this.configService.get('TMDB_BASE_URL') ||
            'https://api.themoviedb.org/3';
        this.apiKey = this.configService.get('TMDB_API_KEY');
        if (!this.apiKey) {
            throw new Error('TMDB_API_KEY non trovata nelle variabili ambiente');
        }
        this.logger.log('tmdb service inizializzato (cache = movies table)');
    }
    async enrichMovie(movie) {
        try {
            const existingMovie = await this.databaseService.findMovieByTitleYear(movie.title, movie.year);
            if (existingMovie && existingMovie.tmdb_id && existingMovie.is_enriched) {
                this.logger.log(`cache hit (gia arricchito): ${movie.title}`);
                return {
                    ...existingMovie,
                    id: movie.id,
                    source: movie.source,
                };
            }
            if (existingMovie && existingMovie.tmdb_id && !existingMovie.is_enriched) {
                this.logger.log(`film trovato nel db ma non arricchito: ${movie.title}, arricchisco ora...`);
                const tmdbMovie = await this.getMovieDetails(existingMovie.tmdb_id);
                if (tmdbMovie) {
                    const enrichedMovie = this.mapTmdbToMovie(movie, tmdbMovie);
                    await this.databaseService.saveMovie({
                        ...enrichedMovie,
                        id: existingMovie.id,
                    });
                    this.logger.log(`film arricchito e salvato: ${movie.title} (tmdb_id: ${tmdbMovie.id})`);
                    return {
                        ...enrichedMovie,
                        id: movie.id,
                        source: movie.source,
                    };
                }
            }
            this.logger.log(`ricerca tmdb: ${movie.title} (${movie.year})`);
            await this.enforceRateLimit();
            let tmdbMovie = null;
            if (movie.id.startsWith('tt')) {
                tmdbMovie = await this.findByImdbId(movie.id);
                if (tmdbMovie) {
                    this.logger.log(`trovato via imdb id: ${tmdbMovie.id} per ${movie.title}`);
                }
            }
            if (!tmdbMovie) {
                tmdbMovie = await this.searchByTitle(movie.title, movie.year);
                if (tmdbMovie) {
                    this.logger.log(`trovato via titolo: ${tmdbMovie.id} per ${movie.title}`);
                }
            }
            if (!tmdbMovie) {
                this.logger.warn(`nessun risultato tmdb per: ${movie.title}`);
                return movie;
            }
            const enrichedMovie = this.mapTmdbToMovie(movie, tmdbMovie);
            await this.databaseService.saveMovie(enrichedMovie);
            this.logger.log(`film arricchito e salvato: ${movie.title} (tmdb_id: ${tmdbMovie.id})`);
            return enrichedMovie;
        }
        catch (error) {
            this.logger.error(`errore enrichment per ${movie.title}: ${error.message}`);
            return movie;
        }
    }
    async enrichMovies(movies, options) {
        const results = {
            successfulMovies: [],
            failedMovies: [],
            successRate: 0,
        };
        for (let i = 0; i < movies.length; i++) {
            const movie = movies[i];
            try {
                const enrichedMovie = await this.enrichMovie(movie);
                if (enrichedMovie.tmdb_id) {
                    results.successfulMovies.push(enrichedMovie);
                }
                else {
                    results.failedMovies.push({
                        movie,
                        error: 'no tmdb match found',
                    });
                }
                if (options?.onProgress) {
                    options.onProgress(i + 1, movies.length, movie.title);
                }
                await new Promise(resolve => setTimeout(resolve, 100));
            }
            catch (error) {
                results.failedMovies.push({
                    movie,
                    error: error.message,
                });
            }
        }
        results.successRate = movies.length > 0
            ? (results.successfulMovies.length / movies.length) * 100
            : 0;
        this.logger.log(`enrichment completato:`);
        this.logger.log(`  successi: ${results.successfulMovies.length}/${movies.length}`);
        this.logger.log(`  falliti: ${results.failedMovies.length}`);
        this.logger.log(`  tasso successo: ${results.successRate.toFixed(2)}%`);
        return results;
    }
    async syncPopularMovies(limit = 10000) {
        this.logger.log(`sync film popolari tmdb (limit: ${limit})`);
        let synced = 0;
        let errors = 0;
        const batchSize = 20;
        const totalPages = Math.ceil(limit / batchSize);
        for (let page = 1; page <= totalPages; page++) {
            try {
                await this.enforceRateLimit();
                const url = `${this.baseUrl}/movie/popular`;
                const response = await (0, rxjs_1.firstValueFrom)(this.httpService.get(url, {
                    params: {
                        api_key: this.apiKey,
                        language: 'it-IT',
                        page: page,
                    },
                }));
                if (!response.data.results || response.data.results.length === 0) {
                    this.logger.warn(`nessun risultato alla pagina ${page}`);
                    break;
                }
                for (const tmdbMovie of response.data.results) {
                    try {
                        const existing = await this.databaseService.findMovieByTmdbId(tmdbMovie.id);
                        if (existing) {
                            this.logger.debug(`skip: ${tmdbMovie.title} (gia presente)`);
                            continue;
                        }
                        const movie = {
                            id: `tmdb_${tmdbMovie.id}`,
                            title: tmdbMovie.title,
                            year: tmdbMovie.release_date
                                ? new Date(tmdbMovie.release_date).getFullYear()
                                : undefined,
                            source: 'TMDB',
                            tmdb_id: tmdbMovie.id,
                            overview: tmdbMovie.overview,
                            poster_url: tmdbMovie.poster_path
                                ? `https://image.tmdb.org/t/p/w500${tmdbMovie.poster_path}`
                                : undefined,
                            backdrop_url: tmdbMovie.backdrop_path
                                ? `https://image.tmdb.org/t/p/original${tmdbMovie.backdrop_path}`
                                : undefined,
                            popularity: tmdbMovie.popularity,
                            tmdb_rating: tmdbMovie.vote_average,
                            vote_count: tmdbMovie.vote_count,
                            genres: [],
                            actors: [],
                            is_enriched: false,
                        };
                        await this.databaseService.saveMovie(movie);
                        synced++;
                        if (synced % 100 === 0) {
                            this.logger.log(`${synced} film sincronizzati`);
                        }
                    }
                    catch (error) {
                        this.logger.error(`errore ${tmdbMovie.title}: ${error.message}`);
                        errors++;
                    }
                }
                await new Promise(resolve => setTimeout(resolve, 300));
            }
            catch (error) {
                this.logger.error(`errore pagina ${page}: ${error.message}`);
                errors++;
            }
        }
        this.logger.log(`sync completato: ${synced} film, ${errors} errori`);
        return { synced, errors };
    }
    async searchByTitle(title, year) {
        try {
            const url = `${this.baseUrl}/search/movie`;
            const response = await (0, rxjs_1.firstValueFrom)(this.httpService.get(url, {
                params: {
                    api_key: this.apiKey,
                    query: title,
                    year: year,
                    language: 'it-IT',
                    include_adult: false,
                },
            }));
            if (!response.data.results || response.data.results.length === 0) {
                return null;
            }
            const bestMatch = response.data.results[0];
            return await this.getMovieDetails(bestMatch.id);
        }
        catch (error) {
            this.logger.error(`errore ricerca titolo ${title}: ${error.message}`);
            return null;
        }
    }
    async findByImdbId(imdbId) {
        try {
            const url = `${this.baseUrl}/find/${imdbId}`;
            const response = await (0, rxjs_1.firstValueFrom)(this.httpService.get(url, {
                params: {
                    api_key: this.apiKey,
                    external_source: 'imdb_id',
                    language: 'it-IT',
                },
            }));
            if (!response.data.movie_results || response.data.movie_results.length === 0) {
                return null;
            }
            const movie = response.data.movie_results[0];
            return await this.getMovieDetails(movie.id);
        }
        catch (error) {
            this.logger.error(`errore ricerca imdb ${imdbId}: ${error.message}`);
            return null;
        }
    }
    async getMovieDetails(tmdbId) {
        try {
            await this.enforceRateLimit();
            const url = `${this.baseUrl}/movie/${tmdbId}`;
            const response = await (0, rxjs_1.firstValueFrom)(this.httpService.get(url, {
                params: {
                    api_key: this.apiKey,
                    language: 'it-IT',
                    append_to_response: 'credits,keywords,videos,releases',
                },
            }));
            return response.data;
        }
        catch (error) {
            this.logger.error(`errore dettagli film ${tmdbId}: ${error.message}`);
            return null;
        }
    }
    async searchForAutocomplete(query, limit = 10) {
        try {
            return await this.databaseService.searchMoviesForAutocomplete(query, limit);
        }
        catch (error) {
            this.logger.error(`errore autocomplete: ${error.message}`);
            return [];
        }
    }
    async enforceRateLimit() {
        const now = Date.now();
        this.requestHistory = this.requestHistory.filter((timestamp) => now - timestamp < this.rateLimitWindow);
        if (this.requestHistory.length >= this.maxRequestsPerWindow) {
            const oldestRequest = this.requestHistory[0];
            const waitTime = this.rateLimitWindow - (now - oldestRequest) + 100;
            this.logger.debug(`rate limit raggiunto, attesa ${waitTime}ms`);
            await new Promise((resolve) => setTimeout(resolve, waitTime));
        }
        this.requestHistory.push(Date.now());
    }
    mapTmdbToMovie(originalMovie, tmdbMovie) {
        const director = tmdbMovie.credits?.crew
            .find((c) => c.job === 'Director')?.name;
        const actors = tmdbMovie.credits?.cast
            .slice(0, 10)
            .map((a) => a.name) || [];
        const keywords = tmdbMovie.keywords?.keywords
            .slice(0, 10)
            .map((k) => k.name) || [];
        const certification = this.extractCertification(tmdbMovie);
        const trailerUrl = this.extractTrailerUrl(tmdbMovie);
        return {
            id: originalMovie.id,
            title: tmdbMovie.title,
            year: tmdbMovie.release_date
                ? new Date(tmdbMovie.release_date).getFullYear()
                : undefined,
            source: originalMovie.source,
            tmdb_id: tmdbMovie.id,
            is_enriched: true,
            genres: tmdbMovie.genres.map((g) => g.name),
            director: director,
            actors: actors,
            overview: tmdbMovie.overview,
            tagline: tmdbMovie.tagline,
            runtime: tmdbMovie.runtime,
            poster_url: tmdbMovie.poster_path
                ? `https://image.tmdb.org/t/p/w500${tmdbMovie.poster_path}`
                : undefined,
            backdrop_url: tmdbMovie.backdrop_path
                ? `https://image.tmdb.org/t/p/original${tmdbMovie.backdrop_path}`
                : undefined,
            tmdb_rating: tmdbMovie.vote_average,
            vote_count: tmdbMovie.vote_count,
            popularity: tmdbMovie.popularity,
            budget: tmdbMovie.budget,
            revenue: tmdbMovie.revenue,
            status: tmdbMovie.status,
            production_companies: tmdbMovie.production_companies.map((c) => c.name),
            production_countries: tmdbMovie.production_countries.map((c) => c.name),
            original_language: tmdbMovie.original_language,
            original_title: tmdbMovie.original_title,
            spoken_languages: tmdbMovie.spoken_languages.map((l) => l.english_name),
            adult: tmdbMovie.adult,
            homepage: tmdbMovie.homepage,
            imdb_id: tmdbMovie.imdb_id,
            keywords: keywords,
            certification: certification,
            trailer_url: trailerUrl,
        };
    }
    extractCertification(tmdbMovie) {
        try {
            const releases = tmdbMovie.releases?.countries || tmdbMovie.release_dates?.results || [];
            const italianRelease = releases.find((r) => r.iso_3166_1 === 'IT');
            if (italianRelease?.certification) {
                return italianRelease.certification;
            }
            const usRelease = releases.find((r) => r.iso_3166_1 === 'US');
            if (usRelease?.certification) {
                return usRelease.certification;
            }
            return undefined;
        }
        catch (error) {
            return undefined;
        }
    }
    extractTrailerUrl(tmdbMovie) {
        try {
            const videos = tmdbMovie.videos?.results || [];
            const trailer = videos.find((v) => v.type === 'Trailer' && v.site === 'YouTube');
            return trailer ? `https://www.youtube.com/watch?v=${trailer.key}` : undefined;
        }
        catch (error) {
            return undefined;
        }
    }
};
exports.TmdbService = TmdbService;
exports.TmdbService = TmdbService = TmdbService_1 = __decorate([
    (0, common_1.Injectable)(),
    __metadata("design:paramtypes", [axios_1.HttpService,
        config_1.ConfigService,
        database_service_1.DatabaseService])
], TmdbService);
//# sourceMappingURL=tmdb.service.js.map