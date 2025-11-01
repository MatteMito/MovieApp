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
                this.logger.log(`cache hit: ${movie.title}`);
                return {
                    ...existingMovie,
                    id: movie.id,
                    source: movie.source,
                };
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
            totalProcessed: 0,
            successRate: 0,
        };
        this.logger.log(`avvio enrichment batch per ${movies.length} film`);
        const moviesToEnrich = [];
        for (const movie of movies) {
            const existing = await this.databaseService.findMovieByTitleYear(movie.title, movie.year);
            if (existing && existing.tmdb_id && existing.is_enriched) {
                const merged = {
                    ...existing,
                    id: movie.id,
                    source: movie.source,
                };
                results.successfulMovies.push(merged);
                this.logger.debug(`cache hit: ${movie.title}`);
            }
            else {
                moviesToEnrich.push(movie);
            }
        }
        this.logger.log(`film da arricchire: ${moviesToEnrich.length}`);
        this.logger.log(`cache hits: ${results.successfulMovies.length}`);
        for (let i = 0; i < moviesToEnrich.length; i++) {
            const movie = moviesToEnrich[i];
            try {
                const enriched = await this.enrichMovie(movie);
                if (enriched.tmdb_id) {
                    results.successfulMovies.push(enriched);
                }
                else {
                    results.failedMovies.push({
                        movie,
                        error: 'tmdb id non trovato'
                    });
                }
                results.totalProcessed++;
                if (options?.onProgress) {
                    const totalCache = results.successfulMovies.length - (moviesToEnrich.length - i - 1);
                    await options.onProgress(results.totalProcessed + results.successfulMovies.length - moviesToEnrich.length + results.totalProcessed, movies.length, movie.title);
                }
                if (results.totalProcessed % 5 === 0) {
                    this.logger.log(`progress: ${results.totalProcessed}/${moviesToEnrich.length} nuovi arricchiti (${results.successfulMovies.length}/${movies.length} totali)`);
                }
            }
            catch (error) {
                this.logger.error(`errore per ${movie.title}: ${error.message}`);
                results.failedMovies.push({
                    movie,
                    error: error.message
                });
                results.totalProcessed++;
            }
            if (moviesToEnrich.length > 50 && i % 5 === 0) {
                await new Promise(resolve => setTimeout(resolve, 100));
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
                        const existing = await this.databaseService.findMovieByTitleYear(tmdbMovie.title, tmdbMovie.release_date ? new Date(tmdbMovie.release_date).getFullYear() : undefined);
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
            return this.enforceRateLimit();
        }
        this.requestHistory.push(now);
    }
    async findByImdbId(imdbId) {
        try {
            const url = `${this.baseUrl}/find/${imdbId}`;
            const response = await (0, rxjs_1.firstValueFrom)(this.httpService.get(url, {
                params: {
                    api_key: this.apiKey,
                    external_source: 'imdb_id',
                    append_to_response: 'credits,keywords,videos,releases',
                },
            }));
            const movie = response.data.movie_results[0];
            if (!movie)
                return null;
            return await this.getMovieDetails(movie.id);
        }
        catch (error) {
            this.logger.error(`errore ricerca imdb ${imdbId}: ${error.message}`);
            return null;
        }
    }
    mapTmdbToMovie(originalMovie, tmdbData) {
        const director = tmdbData.credits?.crew
            ?.find((c) => c.job === 'Director')?.name;
        const actors = tmdbData.credits?.cast
            ?.slice(0, 10)
            .map((c) => c.name) || [];
        const genres = tmdbData.genres?.map((g) => g.name) || [];
        const keywords = tmdbData.keywords?.keywords
            ?.slice(0, 10)
            .map((k) => k.name) || [];
        const productionCompanies = tmdbData.production_companies
            ?.map((c) => c.name) || [];
        const productionCountries = tmdbData.production_countries
            ?.map((c) => c.name) || [];
        const spokenLanguages = tmdbData.spoken_languages
            ?.map((l) => l.english_name) || [];
        const certification = tmdbData.releases?.countries
            ?.find((c) => c.iso_3166_1 === 'IT')?.certification;
        const trailer = tmdbData.videos?.results
            ?.find((v) => v.type === 'Trailer' && v.site === 'YouTube');
        return {
            id: originalMovie.id,
            title: tmdbData.title,
            year: tmdbData.release_date
                ? new Date(tmdbData.release_date).getFullYear()
                : originalMovie.year,
            source: originalMovie.source,
            tmdb_id: tmdbData.id,
            is_enriched: true,
            genres,
            director,
            actors,
            overview: tmdbData.overview,
            tagline: tmdbData.tagline,
            runtime: tmdbData.runtime,
            poster_url: tmdbData.poster_path
                ? `https://image.tmdb.org/t/p/w500${tmdbData.poster_path}`
                : undefined,
            backdrop_url: tmdbData.backdrop_path
                ? `https://image.tmdb.org/t/p/original${tmdbData.backdrop_path}`
                : undefined,
            tmdb_rating: tmdbData.vote_average,
            vote_count: tmdbData.vote_count,
            popularity: tmdbData.popularity,
            budget: tmdbData.budget,
            revenue: tmdbData.revenue,
            status: tmdbData.status,
            production_companies: productionCompanies,
            production_countries: productionCountries,
            original_language: tmdbData.original_language,
            original_title: tmdbData.original_title,
            spoken_languages: spokenLanguages,
            adult: tmdbData.adult,
            homepage: tmdbData.homepage,
            imdb_id: tmdbData.imdb_id,
            keywords,
            certification,
            trailer_url: trailer
                ? `https://www.youtube.com/watch?v=${trailer.key}`
                : undefined,
        };
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