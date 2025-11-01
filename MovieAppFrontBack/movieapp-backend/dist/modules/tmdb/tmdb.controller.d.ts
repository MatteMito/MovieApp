import { TmdbService } from './tmdb.service';
import { DatabaseService } from '../../database/database.service';
interface ApiResponse<T = any> {
    success: boolean;
    data?: T;
    message?: string;
    timestamp: string;
}
export declare class TmdbController {
    private readonly tmdbService;
    private readonly databaseService;
    private readonly logger;
    constructor(tmdbService: TmdbService, databaseService: DatabaseService);
    syncPopularMovies(body: {
        limit?: number;
    }): Promise<ApiResponse>;
    getStats(): Promise<ApiResponse>;
}
export {};
