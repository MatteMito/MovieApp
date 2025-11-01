import { HttpService } from '@nestjs/axios';
import { ConfigService } from '@nestjs/config';
import { DatabaseService } from '../../database/database.service';
import { Movie, TmdbMovieDetails } from '../../common/interfaces/movie.interface';
export declare class TmdbService {
    private readonly httpService;
    private readonly configService;
    private readonly databaseService;
    private readonly logger;
    private readonly baseUrl;
    private readonly apiKey;
    private readonly rateLimitWindow;
    private readonly maxRequestsPerWindow;
    private requestHistory;
    constructor(httpService: HttpService, configService: ConfigService, databaseService: DatabaseService);
    enrichMovie(movie: Movie): Promise<Movie>;
    enrichMovies(movies: Movie[], options?: {
        onProgress?: (processed: number, total: number, currentMovie?: string) => Promise<void>;
    }): Promise<{
        successfulMovies: Movie[];
        failedMovies: Array<{
            movie: Movie;
            error: string;
        }>;
        totalProcessed: number;
        successRate: number;
    }>;
    syncPopularMovies(limit?: number): Promise<{
        synced: number;
        errors: number;
    }>;
    searchByTitle(title: string, year: number | undefined): Promise<TmdbMovieDetails | null>;
    getMovieDetails(tmdbId: number): Promise<TmdbMovieDetails | null>;
    searchForAutocomplete(query: string, limit?: number): Promise<Movie[]>;
    private enforceRateLimit;
    private findByImdbId;
    private mapTmdbToMovie;
}
