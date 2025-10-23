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
        this.logger.log('✅ tmdb service inizializzato con cache e rate limiting');
    }
    async enrichMovie(movie) {
        const cacheKey = this.generateTmdbCacheKey(movie);
        try {
            const existingMovie = await this.databaseService.findMovieByTitleYear(movie.title, movie.year);
            if (existingMovie && existingMovie.tmdb_id) {
                this.logger.log(`✅ film già arricchito in db: ${movie.title} (skip tmdb api)`);
                return {
                    ...existingMovie,
                    id: movie.id,
                    user_rating: movie.user_rating,
                    watched_date: movie.watched_date,
                    user_review: movie.user_review,
                    is_watched: movie.is_watched,
                    source: movie.source,
                };
            }
            const cachedTmdbData = await this.databaseService.getTmdbCache(cacheKey);
            if (cachedTmdbData) {
                this.logger.log(`✅ cache tmdb hit: ${movie.title} (${movie.year})`);
                return this.mapTmdbToMovie(movie, cachedTmdbData);
            }
            this.logger.log(`🔍 ricerca tmdb: ${movie.title} (${movie.year})`);
            await this.enforceRateLimit();
            let tmdbMovie = null;
            if (movie.id.startsWith('tt')) {
                tmdbMovie = await this.findByImdbIdWithCache(movie.id, cacheKey);
                if (tmdbMovie) {
                    this.logger.log(`✅ trovato via imdb id: ${tmdbMovie.id} per ${movie.title}`);
                }
            }
            if (!tmdbMovie) {
                tmdbMovie = await this.searchByTitleWithCache(movie.title, movie.year, cacheKey);
                if (tmdbMovie) {
                    this.logger.log(`✅ trovato via titolo: ${tmdbMovie.id} per ${movie.title}`);
                }
            }
            if (!tmdbMovie) {
                this.logger.warn(`⚠️ nessun risultato tmdb per: ${movie.title}`);
                return movie;
            }
            const director = tmdbMovie.credits?.crew?.find((person) => person.job === 'Director')?.name || 'unknown';
            const cast = tmdbMovie.credits?.cast
                ?.slice(0, 5)
                .map((actor) => actor.name) || [];
            const genres = tmdbMovie.genres?.map((g) => g.name) || [];
            const keywords = tmdbMovie.keywords?.keywords
                ?.slice(0, 10)
                .map((k) => k.name) || [];
            this.logger.log(`📊 dati estratti per ${movie.title}:`);
            this.logger.log(`   regista: ${director}`);
            this.logger.log(`   generi: ${genres.join(', ')}`);
            this.logger.log(`   cast: ${cast.join(', ')}`);
            await this.databaseService.saveTmdbCache(cacheKey, tmdbMovie);
            const enrichedMovie = this.mapTmdbToMovie(movie, tmdbMovie);
            this.logger.log(`✅ film arricchito e cachato: ${movie.title} (tmdb_id: ${tmdbMovie.id})`);
            return enrichedMovie;
        }
        catch (error) {
            this.logger.error(`❌ errore enrichment per ${movie.title}: ${error.message}`);
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
        this.logger.log(`🎬 avvio enrichment batch per ${movies.length} film`);
        const moviesToEnrich = [];
        for (const movie of movies) {
            const existing = await this.databaseService.findMovieByTitleYear(movie.title, movie.year);
            if (existing && existing.tmdb_id) {
                const merged = {
                    ...existing,
                    id: movie.id,
                    user_rating: movie.user_rating,
                    watched_date: movie.watched_date,
                    user_review: movie.user_review,
                    is_watched: movie.is_watched,
                    source: movie.source,
                };
                results.successfulMovies.push(merged);
                this.logger.debug(`skip enrichment (già fatto): ${movie.title}`);
            }
            else {
                moviesToEnrich.push(movie);
            }
        }
        this.logger.log(`📊 film da arricchire: ${moviesToEnrich.length}/${movies.length}`);
        this.logger.log(`✅ film già arricchiti: ${results.successfulMovies.length}`);
        const batchSize = 5;
        for (let i = 0; i < moviesToEnrich.length; i += batchSize) {
            const batch = moviesToEnrich.slice(i, i + batchSize);
            const promises = batch.map(async (movie, batchIndex) => {
                try {
                    const enriched = await this.enrichMovie(movie);
                    const totalProcessed = results.successfulMovies.length + i + batchIndex + 1;
                    if (options?.onProgress) {
                        await options.onProgress(totalProcessed, movies.length, movie.title);
                    }
                    return { success: true, movie: enriched };
                }
                catch (error) {
                    return {
                        success: false,
                        movie,
                        error: error.message,
                    };
                }
            });
            const batchResults = await Promise.all(promises);
            batchResults.forEach((result) => {
                results.totalProcessed++;
                if (result.success) {
                    results.successfulMovies.push(result.movie);
                }
                else {
                    results.failedMovies.push({
                        movie: result.movie,
                        error: result.error,
                    });
                }
            });
            if (i + batchSize < moviesToEnrich.length) {
                await this.delay(2000);
                this.logger.debug(`batch ${Math.floor(i / batchSize) + 1} completato, pausa rate limiting`);
            }
        }
        results.successRate = results.successfulMovies.length / movies.length;
        const enrichedCount = results.successfulMovies.filter((m) => m.tmdb_id).length;
        this.logger.log(`✅ enrichment batch completato: ${enrichedCount}/${movies.length} film arricchiti`);
        return results;
    }
    async findByImdbIdWithCache(imdbId, cacheKey) {
        try {
            const cleanImdbId = imdbId.startsWith('tt') ? imdbId : `tt${imdbId}`;
            await this.enforceRateLimit();
            const url = `${this.baseUrl}/find/${cleanImdbId}`;
            const response = await (0, rxjs_1.firstValueFrom)(this.httpService.get(url, {
                params: {
                    api_key: this.apiKey,
                    external_source: 'imdb_id',
                    language: 'it-IT',
                },
                timeout: 15000,
            }));
            this.recordApiCall();
            if (response.data.movie_results?.length > 0) {
                const movieId = response.data.movie_results[0].id;
                const movieDetails = await this.getMovieDetailsWithCache(movieId, cacheKey);
                return movieDetails;
            }
            return null;
        }
        catch (error) {
            this.logger.error(`errore ricerca imdb id ${imdbId}: ${error.message}`);
            return null;
        }
    }
    async searchByTitleWithCache(title, year, cacheKey) {
        try {
            await this.enforceRateLimit();
            const url = `${this.baseUrl}/search/movie`;
            const response = await (0, rxjs_1.firstValueFrom)(this.httpService.get(url, {
                params: {
                    api_key: this.apiKey,
                    query: title,
                    year: year || undefined,
                    language: 'it-IT',
                    include_adult: false,
                },
                timeout: 15000,
            }));
            this.recordApiCall();
            if (response.data.results?.length > 0) {
                const bestMatch = this.findBestMatch(response.data.results, title, year);
                if (bestMatch) {
                    const movieDetails = await this.getMovieDetailsWithCache(bestMatch.id, cacheKey);
                    return movieDetails;
                }
            }
            return null;
        }
        catch (error) {
            this.logger.error(`errore ricerca titolo ${title}: ${error.message}`);
            return null;
        }
    }
    async getMovieDetailsWithCache(tmdbId, cacheKey) {
        try {
            await this.enforceRateLimit();
            const url = `${this.baseUrl}/movie/${tmdbId}`;
            const response = await (0, rxjs_1.firstValueFrom)(this.httpService.get(url, {
                params: {
                    api_key: this.apiKey,
                    language: 'it-IT',
                    append_to_response: 'credits,keywords,videos,releases',
                },
                timeout: 20000,
            }));
            this.recordApiCall();
            const movieDetails = response.data;
            if (cacheKey) {
                await this.databaseService.saveTmdbCache(cacheKey, movieDetails);
            }
            return movieDetails;
        }
        catch (error) {
            this.logger.error(`errore dettagli film id ${tmdbId}: ${error.message}`);
            return null;
        }
    }
    async enforceRateLimit() {
        const now = Date.now();
        this.requestHistory = this.requestHistory.filter((timestamp) => now - timestamp < this.rateLimitWindow);
        if (this.requestHistory.length >= this.maxRequestsPerWindow) {
            const oldestRequest = Math.min(...this.requestHistory);
            const waitTime = this.rateLimitWindow - (now - oldestRequest) + 100;
            this.logger.debug(`⏳ rate limit raggiunto, attesa ${waitTime}ms`);
            await this.delay(waitTime);
        }
    }
    recordApiCall() {
        this.requestHistory.push(Date.now());
    }
    generateTmdbCacheKey(movie) {
        const titleKey = movie.title
            .toLowerCase()
            .replace(/[^a-z0-9\s]/g, '')
            .replace(/\s+/g, '_')
            .substring(0, 50);
        return `${titleKey}_${movie.year || 'unknown'}`;
    }
    findBestMatch(movies, originalTitle, originalYear) {
        if (!movies || movies.length === 0)
            return null;
        return movies.reduce((best, current) => {
            let currentScore = 0;
            let bestScore = 0;
            const currentTitleLower = current.title.toLowerCase();
            const originalTitleLower = originalTitle.toLowerCase();
            if (currentTitleLower === originalTitleLower) {
                currentScore += 20;
            }
            else if (currentTitleLower.includes(originalTitleLower) ||
                originalTitleLower.includes(currentTitleLower)) {
                currentScore += 10;
            }
            if (best) {
                const bestTitleLower = best.title.toLowerCase();
                if (bestTitleLower === originalTitleLower) {
                    bestScore += 20;
                }
                else if (bestTitleLower.includes(originalTitleLower) ||
                    originalTitleLower.includes(bestTitleLower)) {
                    bestScore += 10;
                }
            }
            if (originalYear && current.release_date) {
                const currentYear = new Date(current.release_date).getFullYear();
                if (currentYear === originalYear) {
                    currentScore += 15;
                }
                else {
                    const yearDiff = Math.abs(currentYear - originalYear);
                    currentScore -= yearDiff;
                }
            }
            if (originalYear && best && best.release_date) {
                const bestYear = new Date(best.release_date).getFullYear();
                if (bestYear === originalYear) {
                    bestScore += 15;
                }
                else {
                    const yearDiff = Math.abs(bestYear - originalYear);
                    bestScore -= yearDiff;
                }
            }
            if (Math.abs(currentScore - bestScore) <= 2) {
                currentScore += (current.popularity || 0) * 0.1;
                if (best)
                    bestScore += (best.popularity || 0) * 0.1;
            }
            return currentScore > bestScore ? current : best;
        });
    }
    mapTmdbToMovie(original, tmdb) {
        return {
            ...original,
            tmdb_id: tmdb.id,
            genres: tmdb.genres?.map((g) => g.name) || [],
            director: tmdb.credits?.crew?.find((c) => c.job === 'Director')?.name,
            actors: tmdb.credits?.cast?.slice(0, 10).map((c) => c.name) || [],
            overview: tmdb.overview,
            tagline: tmdb.tagline,
            poster_url: tmdb.poster_path
                ? `https://image.tmdb.org/t/p/w500${tmdb.poster_path}`
                : undefined,
            backdrop_url: tmdb.backdrop_path
                ? `https://image.tmdb.org/t/p/w1280${tmdb.backdrop_path}`
                : undefined,
            tmdb_rating: tmdb.vote_average,
            vote_count: tmdb.vote_count,
            popularity: tmdb.popularity,
            runtime: tmdb.runtime,
            budget: tmdb.budget,
            revenue: tmdb.revenue,
            status: tmdb.status,
            original_language: tmdb.original_language,
            original_title: tmdb.original_title,
            adult: tmdb.adult,
            homepage: tmdb.homepage,
            imdb_id: tmdb.imdb_id,
            production_companies: tmdb.production_companies?.map((c) => c.name) || [],
            production_countries: tmdb.production_countries?.map((c) => c.name) || [],
            spoken_languages: tmdb.spoken_languages?.map((l) => l.name) || [],
            keywords: tmdb.keywords?.keywords?.map((k) => k.name) || [],
            certification: tmdb.releases?.countries?.find((c) => c.iso_3166_1 === 'IT')?.certification,
            trailer_url: tmdb.videos?.results?.find((v) => v.type === 'Trailer' && v.site === 'YouTube')
                ?.key
                ? `https://www.youtube.com/watch?v=${tmdb.videos.results.find((v) => v.type === 'Trailer' && v.site === 'YouTube')?.key}`
                : undefined,
        };
    }
    delay(ms) {
        return new Promise((resolve) => setTimeout(resolve, ms));
    }
    async healthCheck() {
        try {
            const testUrl = `${this.baseUrl}/configuration`;
            await this.enforceRateLimit();
            const response = await (0, rxjs_1.firstValueFrom)(this.httpService.get(testUrl, {
                params: { api_key: this.apiKey },
                timeout: 10000,
            }));
            this.recordApiCall();
            const cacheStats = await this.databaseService.getTmdbCacheStats();
            return {
                status: 'healthy',
                details: {
                    tmdbApi: 'connected',
                    baseUrl: this.baseUrl,
                    cacheIntegration: 'active',
                    enrichmentOptimization: 'enabled with is_enriched flag',
                    rateLimiting: `${this.requestHistory.length}/${this.maxRequestsPerWindow} requests`,
                    cacheStats: cacheStats,
                    timestamp: new Date().toISOString(),
                },
            };
        }
        catch (error) {
            return {
                status: 'unhealthy',
                details: {
                    error: error.message,
                    cacheIntegration: this.databaseService.isDatabaseAvailable() ? 'active' : 'disabled',
                    timestamp: new Date().toISOString(),
                },
            };
        }
    }
    getApiUsageStats() {
        const now = Date.now();
        const recentCalls = this.requestHistory.filter((timestamp) => now - timestamp < this.rateLimitWindow);
        return {
            recentCalls: recentCalls.length,
            maxAllowed: this.maxRequestsPerWindow,
            rateLimitWindow: `${this.rateLimitWindow / 1000}s`,
            utilizationPercentage: Math.round((recentCalls.length / this.maxRequestsPerWindow) * 100),
            canMakeRequest: recentCalls.length < this.maxRequestsPerWindow,
            nextResetIn: recentCalls.length > 0
                ? Math.max(0, this.rateLimitWindow - (now - Math.min(...recentCalls)))
                : 0,
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